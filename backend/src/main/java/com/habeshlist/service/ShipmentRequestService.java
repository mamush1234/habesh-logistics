package com.habeshlist.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshlist.admin.PlatformSettings;
import com.habeshlist.admin.PlatformSettingsRepository;
import com.habeshlist.dto.ShipmentRequestDtos.*;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.*;
import com.habeshlist.repository.*;
import com.habeshlist.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class ShipmentRequestService {
    private final DeliveryRequestRepository packages;
    private final MarketTripRepository trips;
    private final ShipmentRequestRepository requests;
    private final PlatformSettingsRepository settings;
    private final MarketplaceService marketplace;
    private final PackageNotificationService notifications;
    private final ObjectMapper json;
    private final Clock clock;

    public ShipmentRequestService(DeliveryRequestRepository packages, MarketTripRepository trips,
            ShipmentRequestRepository requests, PlatformSettingsRepository settings, MarketplaceService marketplace,
            PackageNotificationService notifications, ObjectMapper json, Clock clock) {
        this.packages = packages;
        this.trips = trips;
        this.requests = requests;
        this.settings = settings;
        this.marketplace = marketplace;
        this.notifications = notifications;
        this.json = json;
        this.clock = clock;
    }

    private void requireSender() {
        if (SecurityUtils.currentUser().role() != Role.SENDER)
            throw new AccessDeniedException("Only senders can send shipment requests");
    }

    private void requireOwner(DeliveryRequest p) {
        if (p.getSender() == null || !p.getSender().getId().equals(SecurityUtils.currentUser().userId()))
            throw new AccessDeniedException("Not your package");
    }

    private DeliveryRequest ownedPackage(UUID id, boolean lock) {
        requireSender();
        DeliveryRequest p = (lock ? packages.findForUpdate(id) : packages.findById(id))
                .orElseThrow(() -> new NotFoundException("Package not found"));
        requireOwner(p);
        return p;
    }

    @Transactional(readOnly = true)
    public QuoteDto quote(UUID packageId, UUID tripId) {
        return calculate(ownedPackage(packageId, false),
                trips.findById(tripId).orElseThrow(() -> new NotFoundException("Trip not found")));
    }

    private QuoteDto calculate(DeliveryRequest p, MarketTrip trip) {
        if (p.getConvertedShipment() != null || p.getGuestAccount() != null)
            throw new BadRequestException("Admin-managed packages cannot send shipment requests");
        marketplace.requireCompatible(p, trip);
        PlatformSettings s = settings.findById(PlatformSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Platform settings are missing"));
        List<LineDto> lines = PackageDetails.items(p).stream().map(item -> {
            TripItemPrice price = trip.getItemPrices().stream().filter(v -> v.getCategory() == item.category())
                    .findFirst().orElseThrow(() -> new BadRequestException(
                            "No advertised price for " + item.category() + ". Choose another trip or use the existing offer flow."));
            BigDecimal amount = price.getUnit() == TripItemPrice.PriceUnit.PER_KG
                    ? price.getPrice().multiply(item.weightKg()) : price.getPrice();
            return new LineDto(item.category(), item.weightKg(), price.getPrice(), price.getUnit(),
                    amount.setScale(2, RoundingMode.HALF_UP));
        }).toList();
        BigDecimal base = lines.stream().map(LineDto::amount).reduce(new BigDecimal("0.00"), BigDecimal::add);
        if (base.signum() <= 0 || base.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new BadRequestException("Calculated package price is outside the supported range");
        // Include package/trip revisions and settings so a changed review cannot be submitted unnoticed.
        String key = digest(p.getId() + "|" + trip.getId() + "|" + p.getUpdatedAt() + "|" + trip.getExpectedArrivalDate()
                + "|" + trip.getOrigin() + "|" + trip.getDestination() + "|" + trip.getDepartureDate()
                + "|" + lines + "|" + s.getPlatformFeePercent() + "|" + s.getFeePayer() + "|" + s.getUpdatedAt());
        return priceQuote(p.getId(), trip.getId(), trip.getOrigin(), trip.getDestination(), trip.getDepartureDate(),
                lines, base, s.getPlatformFeePercent(), s.getFeePayer(), key);
    }

    private QuoteDto priceQuote(UUID packageId, UUID tripId, String origin, String destination, LocalDate departure,
            List<LineDto> lines, BigDecimal base, BigDecimal percent, PlatformSettings.FeePayer payer, String key) {
        BigDecimal fee = base.multiply(percent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal travelerFee = switch (payer) {
            case SENDER -> new BigDecimal("0.00");
            case TRAVELER -> fee;
            case SPLIT -> fee.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        };
        BigDecimal senderFee = fee.subtract(travelerFee);
        return new QuoteDto(packageId, tripId, origin, destination, departure,
                lines, base, percent, payer, fee, senderFee, travelerFee,
                base.add(senderFee), base.subtract(travelerFee), key);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for shipment quotes", e);
        }
    }

    @Transactional
    public RequestDto submit(SubmitInput input) {
        DeliveryRequest p = ownedPackage(input.packageId(), true);
        MarketTrip trip = trips.findForUpdate(input.tripId()).orElseThrow(() -> new NotFoundException("Trip not found"));
        QuoteDto quote = calculate(p, trip);
        if (!quote.quoteKey().equals(input.quoteKey()))
            throw new BadRequestException("The quote changed. Review the latest price before sending.");
        var existing = requests.findByPendingPackageId(p.getId());
        if (existing.isPresent()) {
            var old = existing.get();
            if (old.getExpiresAt().isAfter(clock.instant())) {
                if (old.getTrip().getId().equals(trip.getId()) && old.getQuoteJson().equals(writeQuote(quote))
                        && Objects.equals(old.getMessage(), clean(input.message()))) return dto(old);
                throw new BadRequestException("This package already has a pending shipment request. Wait for its review or expiry.");
            }
            old.setStatus(ShipmentRequest.Status.EXPIRED);
            old.setPendingPackageId(null);
            requests.flush();
        }
        Instant now = clock.instant();
        ShipmentRequest saved = requests.save(ShipmentRequest.builder().packageRequest(p).trip(trip).sender(p.getSender())
                .status(ShipmentRequest.Status.PENDING).pendingPackageId(p.getId()).quoteJson(writeQuote(quote))
                .message(clean(input.message())).createdAt(now).expiresAt(now.plus(Duration.ofHours(1))).build());
        String notice = "A targeted shipment request is pending for " + trip.getOrigin() + " to " + trip.getDestination()
                + ". Review the package's Shipment requests section. It expires in one hour; no capacity is reserved.";
        notifications.notifyAdmins(p, notice + (trip.getTraveler().getRole() == Role.TRAVELER
                ? " Admin review is required because traveler access is read-only." : ""));
        if (trip.getTraveler().getRole() == Role.AGENT) notifications.notifyRequest(p, trip.getTraveler(), notice);
        notifications.notifyRequest(p, p.getSender(), "Shipment request sent. Check the package for its status. It expires in one hour.");
        return dto(saved);
    }

    @Transactional
    public List<RequestDto> forPackage(UUID packageId) {
        DeliveryRequest p = packages.findById(packageId).orElseThrow(() -> new NotFoundException("Package not found"));
        Role role = SecurityUtils.currentUser().role();
        UUID actorId = SecurityUtils.currentUser().userId();
        if (role == Role.TRAVELER || (role != Role.ADMIN
                && (p.getSender() == null || !p.getSender().getId().equals(actorId)) && role != Role.AGENT))
            throw new AccessDeniedException("Shipment requests are private to the sender, targeted agent and administrators");
        expirePending();
        return requests.findByPackageRequestIdOrderByCreatedAtDesc(packageId).stream()
                .filter(r -> role == Role.ADMIN || r.getSender().getId().equals(actorId)
                        || (role == Role.AGENT && r.getTrip().getTraveler().getId().equals(actorId)))
                .map(this::dto).toList();
    }

    @Transactional
    public void expirePending() {
        requests.expirePending(clock.instant(), LocalDate.now(clock.withZone(ZoneId.systemDefault())));
    }

    private boolean reviewer(ShipmentRequest r) {
        var actor = SecurityUtils.currentUser();
        return actor.role() == Role.ADMIN || (actor.role() == Role.AGENT
                && r.getTrip().getTraveler().getId().equals(actor.userId()));
    }

    @Transactional
    public List<RequestDto> inbox() {
        Role role = SecurityUtils.currentUser().role();
        if (role == Role.TRAVELER) throw new AccessDeniedException("Traveler request management is disabled");
        expirePending();
        return requests.inbox(SecurityUtils.currentUser().userId(), role == Role.ADMIN, role == Role.AGENT)
                .stream().map(this::dto).toList();
    }

    @Transactional
    public RequestDto decide(UUID id, DecisionInput input) {
        Role role = SecurityUtils.currentUser().role();
        if (role == Role.TRAVELER) throw new AccessDeniedException("Traveler request management is disabled");
        var ref = requests.reference(id).orElseThrow(() -> new NotFoundException("Shipment request not found"));
        // Match the existing offer path's lock order: package, trip, then targeted request.
        DeliveryRequest p = packages.findForUpdate(ref.getPackageId()).orElseThrow(() -> new NotFoundException("Package not found"));
        MarketTrip trip = trips.findForUpdate(ref.getTripId()).orElseThrow(() -> new NotFoundException("Trip not found"));
        ShipmentRequest r = requests.findForUpdate(id).orElseThrow(() -> new NotFoundException("Shipment request not found"));
        boolean response = input.action() == Action.CONFIRM_PRICE || input.action() == Action.REJECT_PRICE;
        if (response) {
            requireSender();
            requireOwner(p);
        } else if (!reviewer(r)) {
            throw new AccessDeniedException("Only administrators or the targeted trip's agent can review this request");
        }
        if (r.getStatus() != ShipmentRequest.Status.PENDING)
            throw new BadRequestException("Only pending requests can be reviewed");
        if (!r.getExpiresAt().isAfter(clock.instant()) || p.getStatus() != com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus.REQUESTED
                || !trip.getTraveler().isActive()
                || trip.getDepartureDate().isBefore(LocalDate.now(clock.withZone(ZoneId.systemDefault())))) {
            r.setStatus(ShipmentRequest.Status.EXPIRED);
            r.setPendingPackageId(null);
            return dto(r);
        }
        QuoteDto original = readQuote(r.getQuoteJson());
        QuoteDto proposal = r.getProposedQuoteJson() == null ? null : readQuote(r.getProposedQuoteJson());
        if (response && proposal == null) throw new BadRequestException("There is no revised price to respond to");
        QuoteDto reviewed = response ? proposal : original;
        if (input.quoteKey() == null || !input.quoteKey().equals(reviewed.quoteKey()))
            throw new BadRequestException("The reviewed price changed. Refresh and review it again.");
        switch (input.action()) {
            case PROPOSE_PRICE -> {
                if (input.proposedPrice() == null) throw new BadRequestException("Provide a proposed package price");
                marketplace.requireCompatible(p, trip);
                QuoteDto updated = priceQuote(original.packageId(), original.tripId(), original.origin(), original.destination(),
                        original.departureDate(), List.of(), input.proposedPrice().setScale(2, RoundingMode.HALF_UP),
                        original.platformFeePercent(), original.feePayer(), digest(UUID.randomUUID().toString()));
                r.setProposedQuoteJson(writeQuote(updated));
                notifications.notifyRequest(p, r.getSender(), "A revised shipment price needs your approval before matching. The original one-hour deadline still applies.");
            }
            case DECLINE, REJECT_PRICE -> {
                r.setStatus(ShipmentRequest.Status.DECLINED);
                r.setPendingPackageId(null);
                r.setDecidedAt(clock.instant());
                notifications.notifyRequest(p, r.getSender(), "Shipment request declined. Your package is available for another request.");
                notifications.notifyAdmins(p, "A targeted shipment request was declined.");
            }
            case ACCEPT, CONFIRM_PRICE -> {
                if (input.action() == Action.ACCEPT && proposal != null)
                    throw new BadRequestException("The sender must approve or reject the revised price");
                marketplace.requireCompatible(p, trip);
                r.setAcceptedOffer(marketplace.acceptTargetedRequest(p, trip, reviewed.packageAmount()));
                r.setStatus(ShipmentRequest.Status.ACCEPTED);
                r.setPendingPackageId(null);
                r.setDecidedAt(clock.instant());
                notifications.notifyRequest(p, r.getSender(), "Shipment request accepted and trip capacity reserved. Open the package for Stripe test payment; no real funds or payouts.");
                notifications.notifyAdmins(p, "A targeted shipment request was accepted and capacity reserved.");
            }
        }
        if (trip.getTraveler().getRole() == Role.AGENT && input.action() != Action.PROPOSE_PRICE)
            notifications.notifyRequest(p, trip.getTraveler(), "Targeted shipment request is now " + r.getStatus() + ".");
        return dto(r);
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    private String writeQuote(QuoteDto quote) {
        try {
            return json.writeValueAsString(quote);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not save shipment quote", e);
        }
    }

    private QuoteDto readQuote(String value) {
        try {
            return json.readValue(value, QuoteDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read saved shipment quote", e);
        }
    }

    private RequestDto dto(ShipmentRequest request) {
        boolean pending = request.getStatus() == ShipmentRequest.Status.PENDING && request.getExpiresAt().isAfter(clock.instant());
        return new RequestDto(request.getId(), request.getPackageRequest().getId(), request.getTrip().getId(),
                request.getStatus(), readQuote(request.getQuoteJson()), request.getMessage(),
                request.getCreatedAt(), request.getExpiresAt(), request.getTrip().getTraveler().getRole() == Role.TRAVELER,
                request.getProposedQuoteJson() == null ? null : readQuote(request.getProposedQuoteJson()), request.getDecidedAt(),
                pending && reviewer(request), pending && request.getProposedQuoteJson() != null
                        && SecurityUtils.currentUser().role() == Role.SENDER
                        && request.getSender().getId().equals(SecurityUtils.currentUser().userId()));
    }
}
