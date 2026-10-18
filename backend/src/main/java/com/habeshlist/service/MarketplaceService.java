package com.habeshlist.service;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.MarketplaceDtos.*;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.MarketplaceTypes.*;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.*;
import com.habeshlist.repository.*;
import com.habeshlist.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

@Service
public class MarketplaceService {
    private final MarketTripRepository trips;
    private final DeliveryRequestRepository requests;
    private final DeliveryOfferRepository offers;
    private final DeliveryEventRepository events;
    private final UserRepository users;
    private final TravelerProfileRepository profiles;
    private final PackageNotificationService notifications;
    private final Clock clock;

    public MarketplaceService(MarketTripRepository trips, DeliveryRequestRepository requests,
            DeliveryOfferRepository offers, DeliveryEventRepository events, UserRepository users,
            TravelerProfileRepository profiles, PackageNotificationService notifications, Clock clock) {
        this.trips = trips;
        this.requests = requests;
        this.offers = offers;
        this.events = events;
        this.users = users;
        this.profiles = profiles;
        this.notifications = notifications;
        this.clock = clock;
    }

    private User actor() {
        return users.findById(SecurityUtils.currentUser().userId())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
    private void require(boolean condition, String message) {
        if (!condition) throw new BadRequestException(message);
    }
    private void permit(boolean condition) {
        if (!condition) throw new AccessDeniedException("This action is not available to your account");
    }
    private boolean admin() { return SecurityUtils.currentUser().role() == Role.ADMIN; }
    private boolean offeringRole() {
        Role role = SecurityUtils.currentUser().role();
        return role == Role.TRAVELER || role == Role.AGENT;
    }
    private UUID actorId() { return SecurityUtils.currentUser().userId(); }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.systemDefault())); }
    private DeliveryRequest request(UUID id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException("Request not found"));
    }
    private DeliveryRequest lockRequest(UUID id) {
        return requests.findForUpdate(id).orElseThrow(() -> new NotFoundException("Request not found"));
    }
    private MarketTrip lockTrip(UUID id) {
        return trips.findForUpdate(id).orElseThrow(() -> new NotFoundException("Trip not found"));
    }
    private boolean sender(DeliveryRequest r) { return r.getSender().getId().equals(actorId()); }
    private boolean carrier(DeliveryRequest r) {
        return r.getAcceptedOffer() != null && r.getAcceptedOffer().getTraveler().getId().equals(actorId());
    }
    private boolean visible(DeliveryRequest r) {
        return admin() || sender(r) || carrier(r)
                || (SecurityUtils.currentUser().role() != Role.SENDER && r.getStatus() == DeliveryStatus.REQUESTED);
    }

    @Transactional
    public TripDto createTrip(TripInput input) {
        User traveler = actor();
        permit(offeringRole());
        require(!input.departureDate().isBefore(today()), "Departure date must not be in the past");
        require(input.expectedArrivalDate() == null || !input.expectedArrivalDate().isBefore(input.departureDate()),
                "Arrival date cannot be before departure");
        require(Collections.disjoint(input.allowedItems(), input.blockedItems()), "Allowed and blocked items must not overlap");
        MarketTrip trip = MarketTrip.builder().traveler(traveler).origin(input.origin().trim())
                .destination(input.destination().trim()).departureDate(input.departureDate())
                .expectedArrivalDate(input.expectedArrivalDate()).capacityKg(input.capacityKg())
                .allowedItems(new HashSet<>(input.allowedItems())).blockedItems(new HashSet<>(input.blockedItems()))
                .createdAt(clock.instant()).build();
        return tripDto(trips.save(trip));
    }

    @Transactional(readOnly = true)
    public List<TripDto> listTrips() {
        return trips.findAllByOrderByDepartureDateAscCreatedAtAsc().stream()
                .filter(t -> admin() || t.getTraveler().getId().equals(actorId())
                        || (!offeringRole()
                            && !t.getDepartureDate().isBefore(today()) && t.getTraveler().isActive()))
                .map(this::tripDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.PublicTravelDto> publicTrips() {
        return trips.findAllByOrderByDepartureDateAscCreatedAtAsc().stream()
                .filter(t -> !t.getDepartureDate().isBefore(today()) && t.getTraveler().isActive()
                        && available(t).signum() > 0)
                .limit(6).map(t -> new ApiDtos.PublicTravelDto(t.getId(), t.getOrigin(), t.getDestination(),
                        t.getDepartureDate(), available(t))).toList();
    }

    @Transactional
    public RequestDto createRequest(RequestInput input) {
        User sender = actor();
        permit(sender.getRole() == Role.SENDER);
        require(!input.availableFrom().isBefore(today()) && !input.deadline().isBefore(input.availableFrom()),
                "Choose an available date today or later and a deadline on or after that date");
        DeliveryRequest r = requests.save(DeliveryRequest.builder().sender(sender).pickupCity(input.pickupCity().trim())
                .destinationCity(input.destinationCity().trim()).availableFrom(input.availableFrom()).deadline(input.deadline())
                .itemType(input.itemType()).description(input.description().trim()).weightKg(input.weightKg())
                .offeredPrice(input.offeredPrice()).status(DeliveryStatus.REQUESTED)
                .createdAt(clock.instant()).updatedAt(clock.instant()).build());
        record(r, "Delivery request posted");
        notifications.broadcastRequest(r);
        return requestDto(r);
    }

    @Transactional(readOnly = true)
    public List<RequestDto> listRequests() {
        return requests.findAllByOrderByCreatedAtDesc().stream().filter(this::visible).map(this::requestDto).toList();
    }
    @Transactional(readOnly = true)
    public RequestDto getRequest(UUID id) {
        DeliveryRequest r = request(id);
        permit(visible(r));
        return requestDto(r);
    }

    private BigDecimal available(MarketTrip t) { return t.getCapacityKg().subtract(t.getReservedKg()); }
    private String city(String value) { return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private boolean samePickupRegion(String first, String second) {
        return city(first).equals(city(second)) || (dmvPickup(first) && dmvPickup(second));
    }
    private boolean dmvPickup(String value) {
        String key = city(value).replaceAll("[.,]", "").replaceAll("\\s+", " ");
        if (Set.of("dc", "washington", "washington dc", "washington district of columbia",
                "district of columbia", "md", "maryland", "va", "virginia", "northern virginia",
                "northern va", "nova", "dmv").contains(key)) return true;
        if (key.endsWith(" md") || key.endsWith(" maryland")) return true;
        String locality = key.replaceFirst(" (va|virginia)$", "");
        if (Set.of("alexandria", "alexandira", "arlington", "fairfax", "falls church", "annandale",
                "springfield", "reston", "herndon", "vienna", "mclean", "tysons", "sterling",
                "ashburn", "leesburg", "manassas", "woodbridge", "chantilly", "centreville",
                "dulles").contains(locality)) return true;
        return Set.of("baltimore", "silver spring", "bethesda", "rockville", "gaithersburg",
                "germantown", "college park", "hyattsville", "laurel", "bowie",
                "greenbelt", "takoma park", "national harbor").contains(key);
    }
    private boolean compatible(DeliveryRequest r, MarketTrip t) {
        return incompatibilities(r, t).isEmpty();
    }
    private List<String> incompatibilities(DeliveryRequest r, MarketTrip t) {
        LocalDate arrival = t.getExpectedArrivalDate() == null ? t.getDepartureDate() : t.getExpectedArrivalDate();
        List<String> reasons = new ArrayList<>();
        if (r.getStatus() != DeliveryStatus.REQUESTED) reasons.add("This request is no longer accepting offers");
        if (!r.getSender().isActive() || !t.getTraveler().isActive()) reasons.add("A participant account is inactive");
        if (!samePickupRegion(t.getOrigin(), r.getPickupCity()))
            reasons.add("Trip origin '" + t.getOrigin() + "' must match pickup city '" + r.getPickupCity() + "' or share the DC/Maryland/Northern Virginia pickup region");
        if (!city(t.getDestination()).equals(city(r.getDestinationCity())))
            reasons.add("Trip destination '" + t.getDestination() + "' must match destination city '" + r.getDestinationCity() + "'");
        if (t.getDepartureDate().isBefore(today())) reasons.add("Trip departure date is in the past");
        if (t.getDepartureDate().isBefore(r.getAvailableFrom()))
            reasons.add("Trip departs " + t.getDepartureDate() + " before the package is available on " + r.getAvailableFrom());
        if (arrival.isAfter(r.getDeadline()))
            reasons.add("Trip arrives " + arrival + " after the delivery deadline " + r.getDeadline());
        if (available(t).compareTo(r.getWeightKg()) < 0)
            reasons.add("Trip has " + available(t).toPlainString() + " kg available but the package requires " + r.getWeightKg().toPlainString() + " kg");
        if (!t.getAllowedItems().contains(r.getItemType()) || t.getBlockedItems().contains(r.getItemType()))
            reasons.add("Trip does not allow item type " + r.getItemType());
        return reasons;
    }
    private void requireCompatible(DeliveryRequest r, MarketTrip t) {
        List<String> reasons = incompatibilities(r, t);
        require(reasons.isEmpty(), String.join("; ", reasons));
    }
    private TravelerDto traveler(User user) {
        return traveler(user, admin() || user.getId().equals(actorId()));
    }
    private TravelerDto traveler(User user, boolean revealName) {
        VerificationLevel level = profiles.findById(user.getId()).map(TravelerProfile::getMarketplaceVerification)
                .orElse(VerificationLevel.UNVERIFIED);
        String name = revealName ? user.getFirstName() + " " + user.getLastName()
                : user.getRole() == Role.AGENT ? "Private agent" : "Private traveler";
        return new TravelerDto(user.getId(), name,
                requests.averageRating(user.getId()), requests.ratingCount(user.getId()), level, user.getRole());
    }
    private double reputation(TravelerDto traveler) {
        return (traveler.rating() == null ? 0 : traveler.rating() / 5 * 30)
                + traveler.verification().ordinal() * 10;
    }
    private double senderScore(BigDecimal price, BigDecimal minimum, TravelerDto traveler) {
        return Math.round((50 * Math.min(1, minimum.doubleValue() / price.doubleValue()) + reputation(traveler)) * 100) / 100.0;
    }
    private TripDto tripDto(MarketTrip t) {
        return new TripDto(t.getId(), traveler(t.getTraveler()), t.getOrigin(), t.getDestination(), t.getDepartureDate(),
                t.getExpectedArrivalDate(), t.getCapacityKg(), available(t),
                Set.copyOf(t.getAllowedItems()), Set.copyOf(t.getBlockedItems()));
    }

    @Transactional(readOnly = true)
    public List<MatchDto> matchesForRequest(UUID id) {
        DeliveryRequest r = request(id);
        boolean traveler = offeringRole();
        permit(sender(r) || admin() || (traveler && visible(r)));
        List<MarketTrip> candidates = trips.findAllByOrderByDepartureDateAscCreatedAtAsc().stream()
                .filter(t -> !traveler || t.getTraveler().getId().equals(actorId()))
                .filter(t -> compatible(r, t)).toList();
        Map<UUID, BigDecimal> prices = new HashMap<>();
        offers.findByRequestIdOrderByCreatedAtAsc(id).stream().filter(o -> o.getStatus() == OfferStatus.PENDING)
                .forEach(o -> prices.put(o.getTrip().getId(), o.getPrice()));
        BigDecimal minimum = candidates.stream().map(t -> prices.getOrDefault(t.getId(), r.getOfferedPrice()))
                .min(BigDecimal::compareTo).orElse(r.getOfferedPrice());
        return candidates.stream().map(t -> {
            BigDecimal price = prices.getOrDefault(t.getId(), r.getOfferedPrice());
            return new MatchDto(requestDto(r), tripDto(t), price, senderScore(price, minimum, traveler(t.getTraveler())));
        }).sorted(Comparator.comparingDouble(MatchDto::score).reversed()
                .thenComparing(MatchDto::price).thenComparing(m -> m.trip().id())).toList();
    }

    @Transactional(readOnly = true)
    public List<MatchDto> matchesForTrip(UUID id) {
        MarketTrip t = trips.findById(id).orElseThrow(() -> new NotFoundException("Trip not found"));
        permit(t.getTraveler().getId().equals(actorId()) || admin());
        List<DeliveryRequest> candidates = requests.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> compatible(r, t)).toList();
        BigDecimal maximum = candidates.stream().map(DeliveryRequest::getOfferedPrice).max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
        TravelerDto traveler = traveler(t.getTraveler());
        return candidates.stream().map(r -> new MatchDto(requestDto(r), tripDto(t), r.getOfferedPrice(),
                Math.round((50 * r.getOfferedPrice().doubleValue() / maximum.doubleValue() + reputation(traveler)) * 100) / 100.0))
                .sorted(Comparator.comparingDouble(MatchDto::score).reversed()
                        .thenComparing(m -> m.request().id())).toList();
    }

    @Transactional
    public RequestDto sendOffer(UUID requestId, OfferInput input) {
        User user = actor();
        permit(offeringRole());
        DeliveryRequest r = lockRequest(requestId);
        MarketTrip t = lockTrip(input.tripId());
        permit(t.getTraveler().getId().equals(user.getId()));
        requireCompatible(r, t);
        DeliveryOffer offer = offers.findByRequestIdAndTravelerId(requestId, actorId())
                .orElseGet(() -> DeliveryOffer.builder().request(r).traveler(user).build());
        require(offer.getStatus() != OfferStatus.PENDING, "You already have a pending offer");
        offer.setTrip(t);
        offer.setPrice(input.price());
        offer.setMessage(input.message());
        offer.setStatus(OfferStatus.PENDING);
        offer.setCreatedAt(clock.instant());
        offers.saveAndFlush(offer);
        notifications.notifyRequest(r, r.getSender(), "A traveler or agent sent an offer. Review it in the marketplace.");
        return requestDto(r);
    }

    @Transactional
    public RequestDto acceptOffer(UUID requestId, UUID offerId) {
        DeliveryRequest r = lockRequest(requestId);
        permit(sender(r));
        require(r.getStatus() == DeliveryStatus.REQUESTED, "This request already has an accepted offer or is closed");
        DeliveryOffer offer = offers.findById(offerId).orElseThrow(() -> new NotFoundException("Offer not found"));
        require(offer.getRequest().getId().equals(requestId) && offer.getStatus() == OfferStatus.PENDING,
                "Select a pending offer for this request");
        MarketTrip trip = lockTrip(offer.getTrip().getId());
        requireCompatible(r, trip);
        trip.setReservedKg(trip.getReservedKg().add(r.getWeightKg()));
        for (DeliveryOffer other : offers.findByRequestIdOrderByCreatedAtAsc(requestId)) {
            if (other.getStatus() == OfferStatus.PENDING) {
                other.setStatus(other.getId().equals(offerId) ? OfferStatus.ACCEPTED : OfferStatus.DECLINED);
                notifications.notifyRequest(r, other.getTraveler(), other.getId().equals(offerId)
                        ? "Your offer was accepted. Package chat is open; waiting for demo escrow funding."
                        : "The sender selected another offer for this request.");
            }
        }
        r.setAcceptedOffer(offer);
        r.setStatus(DeliveryStatus.MATCHED);
        record(r, "Sender accepted one offer; trip weight reserved");
        return requestDto(r);
    }

    @Transactional
    public RequestDto decideOffer(UUID requestId, UUID offerId, boolean withdraw) {
        DeliveryRequest r = lockRequest(requestId);
        DeliveryOffer offer = offers.findById(offerId).orElseThrow(() -> new NotFoundException("Offer not found"));
        permit(withdraw ? offer.getTraveler().getId().equals(actorId()) : sender(r));
        require(offer.getRequest().getId().equals(requestId) && offer.getStatus() == OfferStatus.PENDING
                && r.getStatus() == DeliveryStatus.REQUESTED, "Only pending offers can be withdrawn or declined");
        offer.setStatus(withdraw ? OfferStatus.WITHDRAWN : OfferStatus.DECLINED);
        return requestDto(r);
    }

    @Transactional
    public RequestDto fundDemoEscrow(UUID id) {
        DeliveryRequest r = lockRequest(id);
        permit(sender(r));
        require(r.getStatus() == DeliveryStatus.MATCHED && r.getEscrowStatus() == EscrowStatus.NONE,
                "Demo escrow can only be funded once after matching");
        r.setEscrowAmount(r.getAcceptedOffer().getPrice());
        r.setEscrowStatus(EscrowStatus.DEMO_HELD);
        r.setStatus(DeliveryStatus.PAID_ESCROW);
        record(r, "DEMO ONLY: simulated escrow funded; no money moved");
        notifyParticipants(r, "Demo escrow is funded. The parcel is ready for pickup; no real money moved.");
        return requestDto(r);
    }

    @Transactional
    public RequestDto transition(UUID id, TransitionInput input) {
        DeliveryRequest r = lockRequest(id);
        permit(sender(r) || carrier(r) || admin());
        DeliveryStatus from = r.getStatus(), to = input.status();
        boolean resolution = from == DeliveryStatus.DISPUTED;
        if (resolution) {
            permit(admin());
            require((to == DeliveryStatus.COMPLETED || to == DeliveryStatus.CANCELLED)
                    && input.reason() != null && !input.reason().isBlank(),
                    "An admin must resolve the dispute with COMPLETED or CANCELLED and a reason");
        } else {
            switch (to) {
                case PICKED_UP -> { permit(carrier(r) || admin()); require(from == DeliveryStatus.PAID_ESCROW, "Fund demo escrow before pickup"); }
                case IN_TRANSIT -> { permit(carrier(r) || admin()); require(from == DeliveryStatus.PICKED_UP, "Pick up the parcel first"); }
                case DELIVERED -> { permit(carrier(r) || admin()); require(from == DeliveryStatus.IN_TRANSIT, "The parcel must be in transit first"); }
                case COMPLETED -> { permit(sender(r) || admin()); require(from == DeliveryStatus.DELIVERED, "Confirm delivery before completion"); }
                case CANCELLED -> require(from == DeliveryStatus.REQUESTED || from == DeliveryStatus.MATCHED
                        || from == DeliveryStatus.PAID_ESCROW, "Cancellation is only available before pickup; open a dispute instead");
                case DISPUTED -> require((from == DeliveryStatus.PAID_ESCROW || from == DeliveryStatus.PICKED_UP
                        || from == DeliveryStatus.IN_TRANSIT || from == DeliveryStatus.DELIVERED)
                        && input.reason() != null && !input.reason().isBlank(), "A funded, unfinished delivery and a reason are required");
                default -> throw new BadRequestException("Use offer acceptance or demo escrow funding for this transition");
            }
        }
        if (to == DeliveryStatus.COMPLETED || to == DeliveryStatus.CANCELLED) {
            if (to == DeliveryStatus.COMPLETED) {
                require(r.getEscrowStatus() == EscrowStatus.DEMO_HELD, "Demo escrow is not held");
                r.setEscrowStatus(EscrowStatus.DEMO_RELEASED);
            } else {
                if (r.getEscrowStatus() == EscrowStatus.DEMO_HELD) r.setEscrowStatus(EscrowStatus.DEMO_REFUNDED);
                if (r.getAcceptedOffer() != null) {
                    MarketTrip trip = lockTrip(r.getAcceptedOffer().getTrip().getId());
                    trip.setReservedKg(trip.getReservedKg().subtract(r.getWeightKg()));
                }
                offers.findByRequestIdOrderByCreatedAtAsc(id).stream().filter(o -> o.getStatus() == OfferStatus.PENDING)
                        .forEach(o -> o.setStatus(OfferStatus.DECLINED));
            }
        }
        if (to == DeliveryStatus.DISPUTED) r.setDisputeReason(input.reason().trim());
        r.setStatus(to);
        record(r, input.reason() == null || input.reason().isBlank() ? "Delivery moved to " + to : input.reason().trim());
        notifyParticipants(r, "Delivery status changed to " + to + ". Escrow is demo only.");
        return requestDto(r);
    }

    @Transactional
    public RequestDto rate(UUID id, int rating) {
        DeliveryRequest r = lockRequest(id);
        permit(sender(r));
        require(r.getStatus() == DeliveryStatus.COMPLETED && r.getRating() == null, "Rate a completed delivery once");
        require(rating >= 1 && rating <= 5, "Choose a rating from 1 to 5");
        r.setRating(rating);
        return requestDto(r);
    }
    @Transactional
    public TravelerDto verify(UUID id, VerificationLevel level) {
        permit(admin());
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        require(user.getRole() == Role.TRAVELER || user.getRole() == Role.AGENT, "Only travelers and agents can be verified for offers");
        TravelerProfile profile = profiles.findById(id).orElseGet(() -> profiles.save(TravelerProfile.builder().user(user).build()));
        profile.setMarketplaceVerification(level);
        return traveler(profile.getUser());
    }
    private void record(DeliveryRequest r, String description) {
        r.setUpdatedAt(clock.instant());
        events.save(DeliveryEvent.builder().request(r).status(r.getStatus()).actorId(actorId())
                .description(description).createdAt(clock.instant()).sequenceNumber(events.countByRequestId(r.getId())).build());
    }
    private void notifyParticipants(DeliveryRequest r, String message) {
        Stream.concat(Stream.of(r.getSender()),
                r.getAcceptedOffer() == null ? Stream.empty() : Stream.of(r.getAcceptedOffer().getTraveler()))
                .forEach(u -> notifications.notifyRequest(r, u, message));
        if (r.getStatus() == DeliveryStatus.DISPUTED)
            users.findAllByRole(Role.ADMIN).forEach(u -> notifications.notifyRequest(r, u, "A delivery dispute needs review."));
    }
    private ContactDto contact(User user) { return new ContactDto(user.getFirstName() + " " + user.getLastName(), user.getEmail(), user.getPhone()); }
    private RequestDto requestDto(DeliveryRequest r) {
        List<DeliveryOffer> visibleOffers = offers.findByRequestIdOrderByCreatedAtAsc(r.getId()).stream()
                .filter(o -> sender(r) || admin() || o.getTraveler().getId().equals(actorId())).toList();
        BigDecimal min = visibleOffers.stream().filter(o -> o.getStatus() == OfferStatus.PENDING)
                .map(DeliveryOffer::getPrice).min(BigDecimal::compareTo).orElse(r.getOfferedPrice());
        List<OfferDto> offerDtos = visibleOffers.stream().map(o -> new OfferDto(o.getId(), o.getTrip().getId(),
                traveler(o.getTraveler(), admin() || o.getTraveler().getId().equals(actorId())
                        || (sender(r) && r.getAcceptedOffer() != null && r.getAcceptedOffer().getId().equals(o.getId()))),
                o.getPrice(), o.getMessage(), o.getStatus(),
                senderScore(o.getPrice(), min, traveler(o.getTraveler()))))
                .sorted(Comparator.comparingDouble(OfferDto::score).reversed().thenComparing(OfferDto::id)).toList();
        boolean participant = sender(r) || carrier(r) || admin();
        boolean contactsUnlocked = admin() || (r.getEscrowStatus() != EscrowStatus.NONE
                && r.getEscrowStatus() != EscrowStatus.DEMO_REFUNDED);
        return new RequestDto(r.getId(), r.getSender().getId(), r.getPickupCity(), r.getDestinationCity(), r.getAvailableFrom(),
                r.getDeadline(), r.getItemType(), r.getDescription(), r.getWeightKg(), r.getOfferedPrice(), r.getStatus(),
                r.getEscrowStatus(), r.getEscrowAmount(), r.getAcceptedOffer() == null ? null : r.getAcceptedOffer().getId(),
                r.getRating(), participant ? r.getDisputeReason() : null, offerDtos,
                participant ? events.findByRequestIdOrderBySequenceNumberAsc(r.getId()).stream()
                        .map(e -> new EventDto(e.getStatus(), e.getDescription(), e.getCreatedAt())).toList() : List.of(),
                sender(r) || (participant && contactsUnlocked) ? contact(r.getSender()) : null,
                participant && contactsUnlocked && r.getAcceptedOffer() != null ? contact(r.getAcceptedOffer().getTraveler()) : null,
                r.getCreatedAt(), sender(r) || admin() || carrier(r)
                        ? r.getSender().getFirstName() + " " + r.getSender().getLastName() : null);
    }
}
