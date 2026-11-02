package com.habeshlist.agent;

import com.habeshlist.agent.AgentDtos.*;
import com.habeshlist.agent.AgentTypes.AuthorizationStatus;
import com.habeshlist.agent.AgentTypes.CustomerType;
import com.habeshlist.dto.MarketplaceDtos.RequestInput;
import com.habeshlist.dto.MarketplaceDtos.TripInput;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.CommissionStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.*;
import com.habeshlist.service.AuthService;
import com.habeshlist.service.PackageNotificationService;
import com.habeshlist.util.GuestProductPhoto;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;

@Service
public class AgentPortalService {
    static final int DEFAULT_AUTHORIZATION_DAYS = 90;
    private static final int PAGE_SIZE = 10;
    private static final Set<DeliveryStatus> ACTIVE_SHIPMENT = EnumSet.of(DeliveryStatus.MATCHED,
            DeliveryStatus.PAID_ESCROW, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.ARRIVED, DeliveryStatus.DELIVERED,
            DeliveryStatus.DISPUTED);

    private final AgentAccessService access;
    private final AgentCommissionService commissionRates;
    private final AgentCustomerRepository customers;
    private final AgentAuthorizationRepository authorizations;
    private final AgentCommissionRepository commissions;
    private final AgentAuditLogRepository audit;
    private final AgentProfileRepository profiles;
    private final DeliveryRequestRepository requests;
    private final DeliveryEventRepository events;
    private final MarketTripRepository trips;
    private final AuthService auth;
    private final PackageNotificationService notifications;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AgentPortalService(AgentAccessService access, AgentCommissionService commissionRates,
            AgentCustomerRepository customers, AgentAuthorizationRepository authorizations,
            AgentCommissionRepository commissions, AgentAuditLogRepository audit, AgentProfileRepository profiles,
            DeliveryRequestRepository requests, DeliveryEventRepository events, MarketTripRepository trips,
            AuthService auth, PackageNotificationService notifications, Clock clock) {
        this.access = access;
        this.commissionRates = commissionRates;
        this.customers = customers;
        this.authorizations = authorizations;
        this.commissions = commissions;
        this.audit = audit;
        this.profiles = profiles;
        this.requests = requests;
        this.events = events;
        this.trips = trips;
        this.auth = auth;
        this.notifications = notifications;
        this.clock = clock;
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.systemDefault())); }
    private static String name(User u) { return (u.getFirstName() + " " + Objects.toString(u.getLastName(), "")).trim(); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static int pageIndex(Integer page) { return page == null || page < 0 ? 0 : page; }
    private static <T> PageDto<T> page(List<T> all, Integer requested) {
        int page = pageIndex(requested), from = Math.min(page * PAGE_SIZE, all.size());
        return new PageDto<>(all.subList(from, Math.min(from + PAGE_SIZE, all.size())), page, PAGE_SIZE, all.size(),
                (int) Math.ceil(all.size() / (double) PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        User agent = access.currentAgent();
        AgentProfile profile = profiles.findById(agent.getId()).orElseThrow();
        List<AgentCustomer> mine = customers.findByAgentIdOrderByCreatedAtDesc(agent.getId());
        List<DeliveryRequest> packages = requests.findByCreatedByAgentIdOrderByCreatedAtDesc(agent.getId());
        BigDecimal earned = commissions.findByAgentIdOrderByCreatedAtDesc(agent.getId()).stream()
                .filter(c -> c.getStatus() != CommissionStatus.CANCELLED).map(AgentCommission::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardDto(name(agent), agent.getEmail(), profile.getReferralCode(),
                commissionRates.rateFor(agent.getId()),
                mine.stream().filter(c -> c.getCustomer().isActive()).count(),
                packages.stream().filter(r -> ACTIVE_SHIPMENT.contains(r.getStatus())).count(),
                packages.stream().filter(r -> r.getStatus() == DeliveryStatus.REQUESTED
                        || r.getStatus() == DeliveryStatus.DRAFT).count(),
                earned,
                audit.findTop10ByAgentIdOrderByCreatedAtDesc(agent.getId()).stream().map(this::activity).toList(),
                mine.stream().limit(5).map(c -> customer(agent, c)).toList());
    }

    @Transactional
    public CustomerCreatedDto createCustomer(CreateCustomerInput input) {
        User agent = access.currentAgent();
        String email = input.email().strip().toLowerCase(Locale.ROOT);
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Role role = input.customerType() == CustomerType.SENDER ? Role.SENDER : Role.TRAVELER;
        User customer = auth.createPrivilegedUser(input.firstName().strip(), input.lastName().strip(), email,
                input.phone().strip(), password, role);
        customer.setMustChangePassword(true);
        AgentCustomer link = customers.save(AgentCustomer.builder().agent(agent).customer(customer)
                .customerType(input.customerType()).createdAt(clock.instant()).build());
        var grant = input.authorization();
        AgentAuthorization authorization = grant(agent, customer, grant.method(), grant.consentNote(), grant.validDays());
        access.record(agent, customer, authorization, "CUSTOMER_CREATED", "USER", customer.getId(),
                label(input.customerType().name()) + " customer registered");
        return new CustomerCreatedDto(customer(agent, link), new Credentials(email, password),
                authorization(authorization));
    }

    @Transactional(readOnly = true)
    public PageDto<CustomerDto> customers(CustomerType type, String q, Integer page) {
        User agent = access.currentAgent();
        String query = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        Predicate<AgentCustomer> matches = c -> {
            User u = c.getCustomer();
            return query.isEmpty() || name(u).toLowerCase(Locale.ROOT).contains(query)
                    || u.getEmail().toLowerCase(Locale.ROOT).contains(query)
                    || (u.getPhone() != null && u.getPhone().contains(query));
        };
        List<CustomerDto> all = customers.findByAgentIdOrderByCreatedAtDesc(agent.getId()).stream()
                .filter(c -> type == null || c.getCustomerType() == type).filter(matches)
                .map(c -> customer(agent, c)).toList();
        return page(all, page);
    }

    @Transactional(readOnly = true)
    public CustomerDto customer(UUID customerId) {
        User agent = access.currentAgent();
        return customer(agent, access.ownedCustomer(agent, customerId));
    }

    @Transactional
    public PackageDto createPackage(UUID customerId, PackageInput input) {
        User agent = access.currentAgent();
        AgentCustomer link = access.ownedCustomer(agent, customerId);
        User customer = link.getCustomer();
        if (link.getCustomerType() != CustomerType.SENDER || customer.getRole() != Role.SENDER)
            throw new AccessDeniedException("Packages can only be created for sender customers");
        AgentAuthorization authorization = access.requireActiveAuthorization(agent, customer);
        RequestInput d = input.details();
        validateDates(d);
        String photo = clean(input.photoDataUrl()) == null ? null : GuestProductPhoto.prepare(input.photoDataUrl());
        DeliveryStatus status = input.draft() ? DeliveryStatus.DRAFT : DeliveryStatus.REQUESTED;
        DeliveryRequest r = DeliveryRequest.builder().sender(customer).createdByAgent(agent)
                .productPhoto(photo).pickupCity(d.pickupCity().strip()).destinationCity(d.destinationCity().strip())
                .availableFrom(d.availableFrom()).deadline(d.deadline()).itemType(d.itemType())
                .description(d.description().strip()).weightKg(d.weightKg()).offeredPrice(d.offeredPrice())
                .status(status).createdAt(clock.instant()).updatedAt(clock.instant()).build();
        com.habeshlist.service.PackageDetails.apply(r, d);
        requests.save(r);
        event(r, agent, input.draft() ? "Draft saved by agent " + name(agent)
                : "Delivery request posted by agent " + name(agent) + " on behalf of the sender");
        access.record(agent, customer, authorization, input.draft() ? "PACKAGE_DRAFT_SAVED" : "PACKAGE_CREATED",
                "DELIVERY_REQUEST", r.getId(), r.getPickupCity() + " to " + r.getDestinationCity());
        if (!input.draft()) notifications.broadcastRequest(r);
        return pkg(r);
    }

    @Transactional
    public PackageDto submitDraft(UUID customerId, UUID packageId) {
        User agent = access.currentAgent();
        AgentCustomer link = access.ownedCustomer(agent, customerId);
        AgentAuthorization authorization = access.requireActiveAuthorization(agent, link.getCustomer());
        DeliveryRequest r = requests.findForUpdate(packageId).orElseThrow(() -> new NotFoundException("Package not found"));
        if (r.getCreatedByAgent() == null || !r.getCreatedByAgent().getId().equals(agent.getId())
                || r.getSender() == null || !r.getSender().getId().equals(customerId))
            throw new AccessDeniedException("This package is not managed by your agent account");
        if (r.getStatus() != DeliveryStatus.DRAFT) throw new BadRequestException("Only drafts can be submitted");
        if (r.getAvailableFrom().isBefore(today()) || r.getDeadline().isBefore(r.getAvailableFrom()))
            throw new BadRequestException("Draft dates have passed; create a new package with current dates");
        r.setStatus(DeliveryStatus.REQUESTED);
        r.setUpdatedAt(clock.instant());
        event(r, agent, "Draft submitted by agent " + name(agent));
        access.record(agent, link.getCustomer(), authorization, "PACKAGE_SUBMITTED", "DELIVERY_REQUEST", r.getId(), null);
        notifications.broadcastRequest(r);
        return pkg(r);
    }

    @Transactional
    public TripDto createTrip(UUID customerId, TripInput input) {
        User agent = access.currentAgent();
        AgentCustomer link = access.ownedCustomer(agent, customerId);
        User customer = link.getCustomer();
        if (link.getCustomerType() != CustomerType.TRAVELER || customer.getRole() != Role.TRAVELER)
            throw new AccessDeniedException("Trips can only be posted for traveler customers");
        AgentAuthorization authorization = access.requireActiveAuthorization(agent, customer);
        if (input.departureDate().isBefore(today())) throw new BadRequestException("Departure date must not be in the past");
        if (input.expectedArrivalDate() != null && input.expectedArrivalDate().isBefore(input.departureDate()))
            throw new BadRequestException("Arrival date cannot be before departure");
        if (!Collections.disjoint(input.allowedItems(), input.blockedItems()))
            throw new BadRequestException("Allowed and blocked items must not overlap");
        MarketTrip trip = MarketTrip.builder().traveler(customer).createdByAgent(agent)
                .origin(input.origin().strip()).destination(input.destination().strip())
                .departureDate(input.departureDate()).expectedArrivalDate(input.expectedArrivalDate())
                .capacityKg(input.capacityKg()).allowedItems(new HashSet<>(input.allowedItems()))
                .blockedItems(new HashSet<>(input.blockedItems())).createdAt(clock.instant()).build();
        com.habeshlist.service.TripDetails.apply(trip, input);
        trips.save(trip);
        access.record(agent, customer, authorization, "TRIP_CREATED", "MARKET_TRIP", trip.getId(),
                trip.getOrigin() + " to " + trip.getDestination());
        return trip(trip);
    }

    @Transactional(readOnly = true)
    public PageDto<PackageDto> packages(DeliveryStatus status, Integer page) {
        User agent = access.currentAgent();
        return page(requests.findByCreatedByAgentIdOrderByCreatedAtDesc(agent.getId()).stream()
                .filter(r -> status == null || r.getStatus() == status).map(this::pkg).toList(), page);
    }

    @Transactional(readOnly = true)
    public PageDto<TripDto> trips(Integer page) {
        User agent = access.currentAgent();
        return page(trips.findByCreatedByAgentIdOrderByCreatedAtDesc(agent.getId()).stream().map(this::trip).toList(), page);
    }

    @Transactional(readOnly = true)
    public CommissionPageDto commissions(LocalDate from, LocalDate to, CommissionStatus status, Integer page) {
        User agent = access.currentAgent();
        if (from != null && to != null && to.isBefore(from)) throw new BadRequestException("The end date must be on or after the start date");
        ZoneId zone = ZoneId.systemDefault();
        Instant start = from == null ? Instant.EPOCH : from.atStartOfDay(zone).toInstant();
        Instant end = to == null ? Instant.parse("9999-12-31T00:00:00Z") : to.plusDays(1).atStartOfDay(zone).toInstant();
        List<AgentCommission> inRange = commissions
                .findByAgentIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(agent.getId(), start, end);
        BigDecimal pending = sum(inRange, CommissionStatus.PENDING), paid = sum(inRange, CommissionStatus.PAID);
        PageDto<CommissionDto> result = page(inRange.stream().filter(c -> status == null || c.getStatus() == status)
                .map(this::commission).toList(), page);
        return new CommissionPageDto(result.items(), result.page(), result.size(), result.totalItems(),
                result.totalPages(), pending.add(paid), pending, paid);
    }

    @Transactional(readOnly = true)
    public List<AuthorizationDto> authorizations() {
        User agent = access.currentAgent();
        return authorizations.findByAgentIdOrderByGrantedAtDesc(agent.getId()).stream().map(this::authorization).toList();
    }

    @Transactional
    public AuthorizationDto grant(AuthorizationInput input) {
        User agent = access.currentAgent();
        AgentCustomer link = access.ownedCustomer(agent, input.customerId());
        AgentAuthorization authorization = grant(agent, link.getCustomer(), input.method(), input.consentNote(),
                input.validDays());
        return authorization(authorization);
    }

    @Transactional
    public AuthorizationDto revoke(UUID id) {
        User agent = access.currentAgent();
        AgentAuthorization a = authorizations.findById(id)
                .filter(x -> x.getAgent().getId().equals(agent.getId()))
                .orElseThrow(() -> new AccessDeniedException("This authorization is not managed by your agent account"));
        if (a.getStatus() == AuthorizationStatus.ACTIVE) {
            a.setStatus(AuthorizationStatus.REVOKED);
            a.setRevokedAt(clock.instant());
            access.record(agent, a.getCustomer(), a, "AUTHORIZATION_REVOKED", "AUTHORIZATION", a.getId(), null);
        }
        return authorization(a);
    }

    private AgentAuthorization grant(User agent, User customer, AgentTypes.AuthorizationMethod method, String note,
            Integer validDays) {
        Instant now = clock.instant();
        authorizations.findByAgentIdAndCustomerIdAndStatusOrderByExpiresAtDesc(agent.getId(), customer.getId(),
                AuthorizationStatus.ACTIVE).forEach(previous -> {
                    previous.setStatus(AuthorizationStatus.REVOKED);
                    previous.setRevokedAt(now);
                });
        AgentAuthorization a = authorizations.save(AgentAuthorization.builder().agent(agent).customer(customer)
                .status(AuthorizationStatus.ACTIVE).method(method).consentNote(clean(note)).grantedAt(now)
                .expiresAt(now.plus(Duration.ofDays(validDays == null ? DEFAULT_AUTHORIZATION_DAYS : validDays)))
                .build());
        access.record(agent, customer, a, "AUTHORIZATION_GRANTED",         "AUTHORIZATION", a.getId(), "Consent: " + label(method.name()));
        return a;
    }

            private static String label(String enumName) {
                String spaced = enumName.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
                return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
            }

    private void validateDates(RequestInput d) {
        if (d.availableFrom().isBefore(today()) || d.deadline().isBefore(d.availableFrom()))
            throw new BadRequestException("Choose an available date today or later and a deadline on or after that date");
    }

    private void event(DeliveryRequest r, User agent, String description) {
        events.save(DeliveryEvent.builder().request(r).status(r.getStatus()).actorId(agent.getId())
                .description(description).createdAt(clock.instant()).sequenceNumber(events.countByRequestId(r.getId()))
                .build());
    }

    private static BigDecimal sum(List<AgentCommission> list, CommissionStatus status) {
        return list.stream().filter(c -> c.getStatus() == status).map(AgentCommission::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CustomerDto customer(User agent, AgentCustomer c) {
        User u = c.getCustomer();
        AgentAuthorization latest = authorizations.findByAgentIdAndCustomerIdAndStatusOrderByExpiresAtDesc(
                agent.getId(), u.getId(), AuthorizationStatus.ACTIVE).stream().findFirst().orElse(null);
        return new CustomerDto(u.getId(), name(u), u.getEmail(), u.getPhone(), c.getCustomerType(), u.isActive(),
                u.isMustChangePassword(), requests.countBySenderId(u.getId()), trips.countByTravelerId(u.getId()),
                latest == null ? null : authorization(latest), c.getCreatedAt());
    }

    private AuthorizationDto authorization(AgentAuthorization a) {
        String status = a.getStatus() == AuthorizationStatus.REVOKED ? "REVOKED"
                : a.activeAt(clock.instant()) ? "ACTIVE" : "EXPIRED";
        return new AuthorizationDto(a.getId(), a.getCustomer().getId(), name(a.getCustomer()), status, a.getMethod(),
                a.getConsentNote(), a.getGrantedAt(), a.getExpiresAt(), a.getRevokedAt());
    }

    private PackageDto pkg(DeliveryRequest r) {
        return new PackageDto(r.getId(), r.getSender().getId(), name(r.getSender()), r.getPickupCity(),
                r.getDestinationCity(), r.getAvailableFrom(), r.getDeadline(), r.getItemType(), r.getDescription(),
                r.getWeightKg(), r.getOfferedPrice(), r.getStatus(), r.isAdminApproved(), r.getProductPhoto() != null,
                r.getCreatedAt(), r.getUpdatedAt(), com.habeshlist.service.PackageDetails.items(r),
                com.habeshlist.service.PackageDetails.receiver(r));
    }

    private TripDto trip(MarketTrip t) {
        return new TripDto(t.getId(), t.getTraveler().getId(), name(t.getTraveler()), t.getOrigin(), t.getDestination(),
                t.getDepartureDate(), t.getExpectedArrivalDate(), t.getCapacityKg(),
                t.getCapacityKg().subtract(t.getReservedKg()), Set.copyOf(t.getAllowedItems()),
                Set.copyOf(t.getBlockedItems()), t.getCreatedAt(), t.getHandoffLocation(), t.getNotes(),
                com.habeshlist.service.TripDetails.prices(t));
    }

    private CommissionDto commission(AgentCommission c) {
        DeliveryRequest r = c.getRequest();
        return new CommissionDto(c.getId(), r.getId(), r.getPickupCity() + " → " + r.getDestinationCity(),
                c.getCustomer() == null ? null : name(c.getCustomer()), c.getRate(), c.getBaseAmount(), c.getAmount(),
                c.getStatus(), c.getCreatedAt(), c.getPaidAt());
    }

    private ActivityDto activity(AgentAuditLog log) {
        return new ActivityDto(log.getId(), log.getAction(), log.getCustomer() == null ? null : name(log.getCustomer()),
                log.getTargetType(), log.getTargetId(), log.getDetails(), log.getCreatedAt());
    }
}
