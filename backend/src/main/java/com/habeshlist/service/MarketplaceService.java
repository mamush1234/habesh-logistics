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
    private final com.habeshlist.agent.AgentCommissionService agentCommissions;
    private final Clock clock;
    private final ShipmentRequestRepository shipmentRequests;
    private final com.habeshlist.payment.StripePayments stripePayments;
    private final com.habeshlist.review.ReviewService reviews;
    private final com.habeshlist.confirmation.DeliveryConfirmationRepository confirmations;

    public MarketplaceService(MarketTripRepository trips, DeliveryRequestRepository requests,
            DeliveryOfferRepository offers, DeliveryEventRepository events, UserRepository users,
            TravelerProfileRepository profiles, PackageNotificationService notifications,
            com.habeshlist.agent.AgentCommissionService agentCommissions, Clock clock, ShipmentRequestRepository shipmentRequests,
            com.habeshlist.payment.StripePayments stripePayments, com.habeshlist.review.ReviewService reviews,
            com.habeshlist.confirmation.DeliveryConfirmationRepository confirmations) {
        this.trips = trips;
        this.requests = requests;
        this.offers = offers;
        this.events = events;
        this.users = users;
        this.profiles = profiles;
        this.notifications = notifications;
        this.agentCommissions = agentCommissions;
        this.clock = clock;
        this.shipmentRequests = shipmentRequests;
        this.stripePayments = stripePayments;
        this.reviews = reviews;
        this.confirmations = confirmations;
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
    private boolean sender(DeliveryRequest r) { return r.getSender() != null && r.getSender().getId().equals(actorId()); }
    private boolean managesSender(DeliveryRequest r) { return sender(r) || (r.getSender() == null && admin()); }
    private boolean guestOwner(DeliveryRequest r) {
        return r.getGuestAccount() != null && r.getGuestAccount().getId().equals(actorId());
    }
    private boolean carrier(DeliveryRequest r) {
        return r.getAcceptedOffer() != null && r.getAcceptedOffer().getTraveler().getId().equals(actorId());
    }
    private boolean visible(DeliveryRequest r) {
        if (SecurityUtils.currentUser().role() == Role.TRAVELER) {
            return r.isAdminApproved();
        }
        return admin() || sender(r) || guestOwner(r) || carrier(r)
                || (SecurityUtils.currentUser().role() != Role.SENDER && r.getStatus() == DeliveryStatus.REQUESTED);
    }

    @Transactional
    public TripDto createTrip(TripInput input) {
        User traveler = actor();
        permit(offeringRole());
        return tripDto(postTravelerTrip(traveler, input));
    }

    MarketTrip postTravelerTrip(User traveler, TripInput input) {
        require(traveler.getRole() == Role.TRAVELER || traveler.getRole() == Role.AGENT,
                "Only travelers and agents can post a trip");
        require(!input.departureDate().isBefore(today()), "Departure date must not be in the past");
        require(input.expectedArrivalDate() == null || !input.expectedArrivalDate().isBefore(input.departureDate()),
                "Arrival date cannot be before departure");
        require(Collections.disjoint(input.allowedItems(), input.blockedItems()), "Allowed and blocked items must not overlap");
        MarketTrip trip = MarketTrip.builder().traveler(traveler).origin(input.origin().trim())
                .destination(input.destination().trim()).departureDate(input.departureDate())
                .expectedArrivalDate(input.expectedArrivalDate()).capacityKg(input.capacityKg())
                .allowedItems(new HashSet<>(input.allowedItems())).blockedItems(new HashSet<>(input.blockedItems()))
                .createdAt(clock.instant()).build();
        TripDetails.apply(trip, input);
        return trips.save(trip);
    }

    @Transactional(readOnly = true)
    public List<TripDto> listTrips() {
        return trips.findAllByOrderByDepartureDateAscCreatedAtAsc().stream()
                .filter(this::canViewTrip)
                .map(this::tripDto).toList();
    }

    private boolean canViewTrip(MarketTrip t) {
        return admin() || t.getTraveler().getId().equals(actorId())
                || (!offeringRole() && !t.getDepartureDate().isBefore(today()) && t.getTraveler().isActive());
    }

    @Transactional(readOnly = true)
    public TripDto getTrip(UUID id) {
        MarketTrip t = trips.findById(id).orElseThrow(() -> new NotFoundException("Trip not found"));
        permit(canViewTrip(t));
        return tripDto(t);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.PublicMarketplaceTripDto> publicTrips() {
        return trips.findAllByOrderByDepartureDateAscCreatedAtAsc().stream()
                .filter(this::publiclyAvailable)
                .sorted(Comparator.comparing(MarketTrip::getCreatedAt).reversed().thenComparing(MarketTrip::getId))
                .map(this::publicTripDto).toList();
    }

    private boolean publiclyAvailable(MarketTrip t) {
        return !t.getDepartureDate().isBefore(today()) && t.getTraveler().isActive() && available(t).signum() > 0;
    }

    private ApiDtos.PublicMarketplaceTripDto publicTripDto(MarketTrip t) {
        TravelerDto reputation = traveler(t.getTraveler(), false);
        return new ApiDtos.PublicMarketplaceTripDto(t.getId(), t.getOrigin(), t.getDestination(),
                t.getDepartureDate(), available(t), t.getCreatedAt(), t.getExpectedArrivalDate(),
                reputation.rating(), reputation.ratingCount(), reputation.verification(),
                Set.copyOf(t.getAllowedItems()), TripDetails.prices(t));
    }

    @Transactional(readOnly = true)
    public ApiDtos.PublicMarketplaceTripDto publicTrip(UUID id) {
        MarketTrip t = trips.findById(id).orElseThrow(() -> new NotFoundException("Trip not found"));
        if (!publiclyAvailable(t)) throw new NotFoundException("Trip not found");
        return publicTripDto(t);
    }

    private boolean publiclyAvailable(DeliveryRequest r) {
        return r.isAdminApproved() && r.getStatus() == DeliveryStatus.REQUESTED && !r.getDeadline().isBefore(today())
                && (r.getSender() == null || r.getSender().isActive());
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.PublicRequestDto> publicRequests() {
        return requests.findAllByOrderByCreatedAtDesc().stream().filter(this::publiclyAvailable)
                .map(r -> new ApiDtos.PublicRequestDto(r.getId(), r.getPickupCity(), r.getDestinationCity(),
                        r.getAvailableFrom(), r.getDeadline(), r.getItemType(), r.getWeightKg(), r.getOfferedPrice(),
                        r.getProductPhoto() != null, r.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public String publicProductPhoto(UUID id) {
        DeliveryRequest r = request(id);
        if (!publiclyAvailable(r) || r.getProductPhoto() == null) throw new NotFoundException("Product photo not found");
        return r.getProductPhoto();
    }

    @Transactional
    public RequestDto createRequest(RequestInput input) {
        User sender = actor();
        permit(sender.getRole() == Role.SENDER);
        return requestDto(postSenderRequest(sender, input));
    }

    DeliveryRequest postSenderRequest(User sender, RequestInput input) {
        require(sender.getRole() == Role.SENDER, "Only senders can post a delivery request");
        require(!input.availableFrom().isBefore(today()) && !input.deadline().isBefore(input.availableFrom()),
                "Choose an available date today or later and a deadline on or after that date");
        DeliveryRequest r = DeliveryRequest.builder().sender(sender).pickupCity(input.pickupCity().trim())
                .destinationCity(input.destinationCity().trim()).availableFrom(input.availableFrom()).deadline(input.deadline())
                .itemType(input.itemType()).description(input.description().trim()).weightKg(input.weightKg())
                .offeredPrice(input.offeredPrice()).status(DeliveryStatus.REQUESTED)
                .createdAt(clock.instant()).updatedAt(clock.instant()).build();
        PackageDetails.apply(r, input);
        requests.save(r);
        events.save(DeliveryEvent.builder().request(r).status(r.getStatus()).actorId(sender.getId())
                .description("Delivery request posted").createdAt(clock.instant()).sequenceNumber(0).build());
        notifications.broadcastRequest(r);
        return r;
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

    @Transactional
    public RequestDto setApproval(UUID id, boolean approved) {
        permit(admin());
        DeliveryRequest r = lockRequest(id);
        require(!approved || r.getStatus() != DeliveryStatus.DRAFT, "Draft requests must be submitted before approval");
        if (r.isAdminApproved() != approved) {
            r.setAdminApproved(approved);
            record(r, approved ? "Admin approved request for read-only traveler visibility"
                    : "Admin removed request from traveler and public visibility");
            if (approved) notifications.notifyTravelersOfApproval(r);
        }
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
        if ((r.getSender() != null && !r.getSender().isActive()) || !t.getTraveler().isActive()) reasons.add("A participant account is inactive");
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
        for (ItemType category : PackageDetails.categories(r)) {
            if (!t.getAllowedItems().contains(category) || t.getBlockedItems().contains(category))
                reasons.add("Trip does not allow item type " + category);
        }
        return reasons;
    }
    void requireCompatible(DeliveryRequest r, MarketTrip t) {
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
                Set.copyOf(t.getAllowedItems()), Set.copyOf(t.getBlockedItems()), t.getCreatedAt(),
                t.getHandoffLocation(), t.getNotes(), TripDetails.prices(t));
    }

    @Transactional(readOnly = true)
    public List<MatchDto> matchesForRequest(UUID id) {
        DeliveryRequest r = request(id);
        permit(visible(r));
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
                .filter(this::visible)
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
        permit(user.getRole() == Role.AGENT);
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
        if (r.getSender() == null) {
            notifications.notifyAdmins(r, "A traveler or agent offered to carry a guest package. Review the offer and contact the guest sender.");
        } else {
            notifications.notifyRequest(r, r.getSender(), "A traveler or agent sent an offer. Review it in the marketplace.");
        }
        return requestDto(r);
    }

    @Transactional
    public RequestDto acceptOffer(UUID requestId, UUID offerId) {
        permit(SecurityUtils.currentUser().role() != Role.TRAVELER);
        DeliveryRequest r = lockRequest(requestId);
        permit(managesSender(r));
        require(r.getStatus() == DeliveryStatus.REQUESTED, "This request already has an accepted offer or is closed");
        DeliveryOffer offer = offers.findById(offerId).orElseThrow(() -> new NotFoundException("Offer not found"));
        require(offer.getRequest().getId().equals(requestId) && offer.getStatus() == OfferStatus.PENDING,
                "Select a pending offer for this request");
        require(offer.getTraveler().getRole() != Role.TRAVELER,
                "Traveler request management is disabled; only agent offers can be accepted");
        MarketTrip trip = lockTrip(offer.getTrip().getId());
        requireCompatible(r, trip);
        trip.setReservedKg(trip.getReservedKg().add(r.getWeightKg()));
        for (DeliveryOffer other : offers.findByRequestIdOrderByCreatedAtAsc(requestId)) {
            if (other.getStatus() == OfferStatus.PENDING) {
                other.setStatus(other.getId().equals(offerId) ? OfferStatus.ACCEPTED : OfferStatus.DECLINED);
                notifications.notifyRequest(r, other.getTraveler(), other.getId().equals(offerId)
                        ? r.getSender() == null
                            ? "Your offer for a guest request was accepted. Contact an admin to coordinate pickup."
                            : "Your offer was accepted. Package chat opens after demo escrow funding."
                        : "The sender selected another offer for this request.");
            }
        }
        r.setAcceptedOffer(offer);
        r.setStatus(DeliveryStatus.MATCHED);
        record(r, r.getSender() == null ? "Admin accepted an offer for the guest sender; trip weight reserved"
                : "Sender accepted one offer; trip weight reserved");
        return requestDto(r);
    }

    @Transactional
    public RequestDto decideOffer(UUID requestId, UUID offerId, boolean withdraw) {
        permit(SecurityUtils.currentUser().role() != Role.TRAVELER);
        DeliveryRequest r = lockRequest(requestId);
        DeliveryOffer offer = offers.findById(offerId).orElseThrow(() -> new NotFoundException("Offer not found"));
        permit(withdraw ? offer.getTraveler().getId().equals(actorId()) : managesSender(r));
        require(offer.getRequest().getId().equals(requestId) && offer.getStatus() == OfferStatus.PENDING
                && r.getStatus() == DeliveryStatus.REQUESTED, "Only pending offers can be withdrawn or declined");
        offer.setStatus(withdraw ? OfferStatus.WITHDRAWN : OfferStatus.DECLINED);
        return requestDto(r);
    }

    DeliveryOffer acceptTargetedRequest(DeliveryRequest r, MarketTrip trip, BigDecimal price) {
        requireCompatible(r, trip);
        DeliveryOffer selected = offers.findByRequestIdAndTravelerId(r.getId(), trip.getTraveler().getId())
                .orElseGet(() -> DeliveryOffer.builder().request(r).traveler(trip.getTraveler()).build());
        selected.setTrip(trip);
        selected.setPrice(price);
        selected.setMessage("Accepted targeted shipment request");
        selected.setStatus(OfferStatus.ACCEPTED);
        selected.setCreatedAt(clock.instant());
        offers.saveAndFlush(selected);
        offers.findByRequestIdOrderByCreatedAtAsc(r.getId()).stream()
                .filter(o -> o.getStatus() == OfferStatus.PENDING).forEach(o -> o.setStatus(OfferStatus.DECLINED));
        trip.setReservedKg(trip.getReservedKg().add(r.getWeightKg()));
        r.setAcceptedOffer(selected);
        r.setStatus(DeliveryStatus.MATCHED);
        record(r, "Targeted shipment request accepted; trip weight reserved. Stripe test payment available; no real funds.");
        return selected;
    }

    @Transactional
    public RequestDto fundDemoEscrow(UUID id) {
        permit(SecurityUtils.currentUser().role() != Role.TRAVELER);
        DeliveryRequest r = lockRequest(id);
        permit(managesSender(r));
        require(r.getStatus() == DeliveryStatus.MATCHED && r.getEscrowStatus() == EscrowStatus.NONE,
                "Demo escrow can only be funded once after matching");
        require(!shipmentRequests.existsByAcceptedOfferId(r.getAcceptedOffer().getId()),
                "Targeted shipment requests require Stripe test payment; demo funding is not available");
        r.setEscrowAmount(r.getAcceptedOffer().getPrice());
        r.setEscrowStatus(EscrowStatus.DEMO_HELD);
        r.setStatus(DeliveryStatus.PAID_ESCROW);
        record(r, "DEMO ONLY: simulated escrow funded; no money moved");
        notifyParticipants(r, "Demo escrow is funded. The parcel is ready for pickup; no real money moved.");
        return requestDto(r);
    }

    @Transactional
    public RequestDto transition(UUID id, TransitionInput input) {
        permit(SecurityUtils.currentUser().role() != Role.TRAVELER);
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
                case PICKED_UP -> { permit(carrier(r) || admin()); require(from == DeliveryStatus.PAID_ESCROW, "Confirm payment before pickup"); }
                case IN_TRANSIT -> { permit(carrier(r) || admin()); require(from == DeliveryStatus.PICKED_UP, "Pick up the parcel first"); }
                case ARRIVED -> { permit(carrier(r) || admin()); require(from == DeliveryStatus.IN_TRANSIT, "The parcel must be in transit before arrival"); }
                case DELIVERED -> {
                    permit(carrier(r) || admin());
                    require(from == DeliveryStatus.ARRIVED, "Record arrival before delivery");
                    require(r.getSender() == null || !r.isDeliveryCodeRequired(), "Use the receiver delivery code to confirm this booking");
                }
                case COMPLETED -> {
                    permit(sender(r) || admin());
                    require(from == DeliveryStatus.DELIVERED, "Confirm delivery before completion");
                    require(r.getSender() == null || !r.isDeliveryCodeRequired()
                            || confirmations.findById(id).filter(c -> c.getConfirmedAt() != null).isPresent(),
                            "Use the receiver delivery code to complete this booking");
                }
                case CANCELLED -> require(from == DeliveryStatus.REQUESTED || from == DeliveryStatus.MATCHED
                        || from == DeliveryStatus.PAID_ESCROW, "Cancellation is only available before pickup; open a dispute instead");
                case DISPUTED -> require((from == DeliveryStatus.PAID_ESCROW || from == DeliveryStatus.PICKED_UP
                        || from == DeliveryStatus.IN_TRANSIT || from == DeliveryStatus.ARRIVED || from == DeliveryStatus.DELIVERED)
                        && input.reason() != null && !input.reason().isBlank(), "A funded, unfinished delivery and a reason are required");
                default -> throw new BadRequestException("Use offer acceptance or demo escrow funding for this transition");
            }
        }
        boolean stripeTest = r.getEscrowStatus() == EscrowStatus.STRIPE_TEST_HELD;
        closeEscrow(r, to);
        if (to == DeliveryStatus.DISPUTED) r.setDisputeReason(input.reason().trim());
        r.setStatus(to);
        if (to == DeliveryStatus.COMPLETED && !stripeTest) agentCommissions.recordCompletedDelivery(r);
        record(r, input.reason() == null || input.reason().isBlank() ? "Delivery moved to " + to : input.reason().trim());
        notifyParticipants(r, "Delivery status changed to " + to + ". Test/demo only; no real funds or payouts.");
        return requestDto(r);
    }

    private void closeEscrow(DeliveryRequest r, DeliveryStatus to) {
        boolean stripeTest = r.getEscrowStatus() == EscrowStatus.STRIPE_TEST_HELD;
        if (to == DeliveryStatus.COMPLETED || to == DeliveryStatus.CANCELLED) {
            if (to == DeliveryStatus.COMPLETED) {
                require(r.getEscrowStatus() == EscrowStatus.DEMO_HELD || stripeTest, "Payment is not confirmed");
                r.setEscrowStatus(stripeTest ? EscrowStatus.STRIPE_TEST_RELEASED : EscrowStatus.DEMO_RELEASED);
            } else {
                stripePayments.cancelFor(r);
                if (r.getEscrowStatus() == EscrowStatus.DEMO_HELD) r.setEscrowStatus(EscrowStatus.DEMO_REFUNDED);
                if (r.getAcceptedOffer() != null) {
                    MarketTrip trip = lockTrip(r.getAcceptedOffer().getTrip().getId());
                    trip.setReservedKg(trip.getReservedKg().subtract(r.getWeightKg()));
                }
                offers.findByRequestIdOrderByCreatedAtAsc(r.getId()).stream().filter(o -> o.getStatus() == OfferStatus.PENDING)
                        .forEach(o -> o.setStatus(OfferStatus.DECLINED));
            }
        }
    }

    @Transactional
    public void completeVerifiedDelivery(UUID id) {
        DeliveryRequest r = lockRequest(id);
        permit(admin() || (SecurityUtils.currentUser().role() == Role.AGENT && carrier(r)));
        require(r.getSender() != null && r.isDeliveryCodeRequired() && r.getStatus() == DeliveryStatus.ARRIVED,
                "Only arrived code-protected bookings can be completed with a delivery code");
        var confirmation = confirmations.findById(id).orElseThrow(() -> new BadRequestException("Delivery code not verified"));
        require(confirmation.getConfirmedAt() != null && actorId().equals(confirmation.getConfirmedBy()), "Delivery code not verified");
        boolean stripeTest = r.getEscrowStatus() == EscrowStatus.STRIPE_TEST_HELD;
        closeEscrow(r, DeliveryStatus.COMPLETED);
        r.setStatus(DeliveryStatus.DELIVERED);
        record(r, "Receiver delivery code verified; package delivered");
        r.setStatus(DeliveryStatus.COMPLETED);
        record(r, "Delivery completed using receiver code. Test/demo release only; no real payout");
        if (!stripeTest) agentCommissions.recordCompletedDelivery(r);
        notifyParticipants(r, "Receiver code confirmed delivery. Booking completed; test/demo release only, no real payout.");
    }

    @Transactional
    public RequestDto rate(UUID id, int rating) {
        reviews.submitLegacy(id, rating);
        return requestDto(lockRequest(id));
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
        Stream.concat(Stream.ofNullable(r.getSender()),
                r.getAcceptedOffer() == null ? Stream.empty() : Stream.of(r.getAcceptedOffer().getTraveler()))
                .forEach(u -> notifications.notifyRequest(r, u, message));
        if (r.getSender() == null) notifications.notifyAdmins(r, message + " Contact the guest sender to coordinate.");
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
        boolean participant = sender(r) || guestOwner(r) || carrier(r) || admin();
        boolean contactsUnlocked = admin() || (r.getEscrowStatus() != EscrowStatus.NONE
                && r.getEscrowStatus() != EscrowStatus.DEMO_REFUNDED
                && r.getEscrowStatus() != EscrowStatus.STRIPE_TEST_REFUNDED);
        return new RequestDto(r.getId(), r.getSender() == null
                ? r.getGuestAccount() == null ? null : r.getGuestAccount().getId() : r.getSender().getId(),
                r.getPickupCity(), r.getDestinationCity(), r.getAvailableFrom(),
                r.getDeadline(), r.getItemType(), r.getDescription(), r.getWeightKg(), r.getOfferedPrice(), r.getStatus(),
                r.getEscrowStatus(), r.getEscrowAmount(), r.getAcceptedOffer() == null ? null : r.getAcceptedOffer().getId(),
                r.getRating(), participant ? r.getDisputeReason() : null, offerDtos,
                participant ? events.findByRequestIdOrderBySequenceNumberAsc(r.getId()).stream()
                        .map(e -> new EventDto(e.getStatus(), e.getDescription(), e.getCreatedAt())).toList() : List.of(),
                r.getSender() == null
                        ? admin() || guestOwner(r) ? new ContactDto(r.getGuestName(), r.getGuestEmail(), r.getGuestPhone()) : null
                        : sender(r) || (participant && contactsUnlocked) ? contact(r.getSender()) : null,
                (sender(r) || carrier(r) || admin()) && contactsUnlocked && r.getAcceptedOffer() != null ? contact(r.getAcceptedOffer().getTraveler()) : null,
                r.getCreatedAt(), r.getSender() == null ? admin() || guestOwner(r) ? r.getGuestName() : null
                        : sender(r) || admin() || carrier(r)
                            ? r.getSender().getFirstName() + " " + r.getSender().getLastName() : null,
                r.getSender() == null, r.getProductPhoto() != null,
                admin() && r.getConvertedShipment() != null ? r.getConvertedShipment().getId() : null,
                admin() && r.getConvertedShipment() != null ? r.getConvertedShipment().getTrackingNumber() : null,
                r.isAdminApproved(), PackageDetails.items(r),
                admin() || sender(r) || (carrier(r) && contactsUnlocked) ? PackageDetails.receiver(r) : null,
                r.getAcceptedOffer() != null && shipmentRequests.existsByAcceptedOfferId(r.getAcceptedOffer().getId()));
    }

    @Transactional(readOnly = true)
    public String productPhoto(UUID id) {
        DeliveryRequest r = request(id);
        permit(visible(r));
        if (r.getProductPhoto() == null) throw new NotFoundException("Product photo not found");
        return r.getProductPhoto();
    }
}
