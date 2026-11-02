package com.habeshlist.admin;

import com.habeshlist.admin.AdminDtos.*;
import com.habeshlist.agent.AgentAuditLog;
import com.habeshlist.agent.AgentAuditLogRepository;
import com.habeshlist.agent.AgentCommission;
import com.habeshlist.agent.AgentCommissionRepository;
import com.habeshlist.dto.MarketplaceDtos.TransitionInput;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.CommissionStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.EscrowStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.VerificationLevel;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.repository.*;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.service.MarketplaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Read models for the admin console. Aggregation happens in memory, which suits the current
 * marketplace volume; move hot paths to repository queries if the tables grow large.
 */
@Service
@Transactional(readOnly = true)
public class AdminConsoleService {
    public static final int PAGE_SIZE = 10;
    private static final Set<DeliveryStatus> ACTIVE = EnumSet.of(DeliveryStatus.MATCHED,
            DeliveryStatus.PAID_ESCROW, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.ARRIVED, DeliveryStatus.DELIVERED);
    private static final Set<DeliveryStatus> IN_FLIGHT = EnumSet.of(DeliveryStatus.PAID_ESCROW,
            DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.ARRIVED, DeliveryStatus.DELIVERED);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final UserRepository users;
    private final TravelerProfileRepository travelers;
    private final AgentProfileRepository agents;
    private final MarketTripRepository trips;
    private final DeliveryRequestRepository requests;
    private final DeliveryOfferRepository offers;
    private final DeliveryEventRepository events;
    private final AgentAuditLogRepository audit;
    private final AgentCommissionRepository commissions;
    private final PlatformSettingsRepository settings;
    private final MarketplaceService marketplace;
    private final Clock clock;
    private final com.habeshlist.review.DeliveryReviewRepository reviews;

    public AdminConsoleService(UserRepository users, TravelerProfileRepository travelers,
            AgentProfileRepository agents, MarketTripRepository trips, DeliveryRequestRepository requests,
            DeliveryOfferRepository offers, DeliveryEventRepository events, AgentAuditLogRepository audit,
            AgentCommissionRepository commissions, PlatformSettingsRepository settings,
            MarketplaceService marketplace, Clock clock, com.habeshlist.review.DeliveryReviewRepository reviews) {
        this.users = users;
        this.reviews = reviews;
        this.travelers = travelers;
        this.agents = agents;
        this.trips = trips;
        this.requests = requests;
        this.offers = offers;
        this.events = events;
        this.audit = audit;
        this.commissions = commissions;
        this.settings = settings;
        this.marketplace = marketplace;
        this.clock = clock;
    }

    // ---------- Overview ----------

    public OverviewDto overview(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        BigDecimal feePercent = currentSettings().getPlatformFeePercent();
        List<DeliveryRequest> all = requests.findAll();
        List<DeliveryEvent> allEvents = events.findAll();
        Map<UUID, Instant> fundedAt = firstEventAt(allEvents, DeliveryStatus.PAID_ESCROW);
        Map<UUID, Instant> matchedAt = firstEventAt(allEvents, DeliveryStatus.MATCHED);

        boolean weekly = ChronoUnit.DAYS.between(range.from, range.to) > 31;
        Map<LocalDate, BigDecimal> revenue = new TreeMap<>();
        Map<LocalDate, Long> bookings = new TreeMap<>();
        for (LocalDate d = bucket(range.from, weekly); !d.isAfter(range.to); d = d.plusDays(weekly ? 7 : 1)) {
            revenue.put(d, BigDecimal.ZERO);
            bookings.put(d, 0L);
        }
        BigDecimal total = BigDecimal.ZERO;
        long totalBookings = 0;
        for (DeliveryRequest r : all) {
            Instant funded = fundedAt.get(r.getId());
            if (isRevenue(r) && funded != null && range.contains(funded)) {
                total = total.add(r.getEscrowAmount());
                revenue.merge(bucket(day(funded), weekly), r.getEscrowAmount(), BigDecimal::add);
            }
            Instant matched = matchedAt.get(r.getId());
            if (matched != null && range.contains(matched)) {
                totalBookings++;
                bookings.merge(bucket(day(matched), weekly), 1L, Long::sum);
            }
        }
        List<ChartPointDto> chart = revenue.keySet().stream()
                .map(d -> new ChartPointDto(d, money(revenue.get(d)), bookings.getOrDefault(d, 0L))).toList();
        long active = all.stream().filter(r -> ACTIVE.contains(r.getStatus())).count();
        long pendingReviews = all.stream().filter(this::awaitingReview).count();
        return new OverviewDto(range.from, range.to, weekly ? "WEEK" : "DAY", feePercent, money(total),
                fee(total, feePercent), active, pendingReviews, totalBookings, chart, activities(range, allEvents));
    }

    private List<ActivityDto> activities(Range range, List<DeliveryEvent> allEvents) {
        Stream<ActivityDto> deliveries = allEvents.stream().filter(e -> range.contains(e.getCreatedAt()))
                .map(e -> new ActivityDto("DELIVERY", label(e.getStatus().name()) + ": " + route(e.getRequest()),
                        e.getDescription(), e.getCreatedAt()));
        Stream<ActivityDto> agentActions = audit.findAll().stream().filter(a -> range.contains(a.getCreatedAt()))
                .map(this::auditActivity);
        Stream<ActivityDto> signups = users.findAll().stream()
                .filter(u -> u.getCreatedAt() != null && range.contains(u.getCreatedAt()))
                .map(u -> new ActivityDto("USER", "New " + label(u.getRole().name()) + " account",
                        name(u) + " · " + u.getEmail(), u.getCreatedAt()));
        return Stream.of(deliveries, agentActions, signups).flatMap(Function.identity())
                .sorted(Comparator.comparing(ActivityDto::at).reversed()).limit(10).toList();
    }

    private ActivityDto auditActivity(AgentAuditLog a) {
        String detail = name(a.getAgent()) + (a.getCustomer() == null ? "" : " for " + name(a.getCustomer()));
        return new ActivityDto("AGENT", "Agent: " + label(a.getAction()), detail, a.getCreatedAt());
    }

    // ---------- Users ----------

    public ListDto<UserRowDto> users(String type, String q, String status, int page) {
        Role role = parseRole(type);
        Map<UUID, VerificationLevel> verification = travelers.findAll().stream()
                .collect(Collectors.toMap(TravelerProfile::getUserId, TravelerProfile::getMarketplaceVerification));
        Map<UUID, Boolean> agentActive = agents.findAll().stream()
                .collect(Collectors.toMap(AgentProfile::getUserId, AgentProfile::isActive));
        List<DeliveryRequest> allRequests = requests.findAll();
        Map<UUID, Long> shipments = allRequests.stream().flatMap(r -> Stream.of(r.getSender(), r.getCreatedByAgent())
                .filter(Objects::nonNull).map(User::getId).distinct())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<UUID, Long> tripCounts = trips.findAll().stream().flatMap(t -> Stream.of(t.getTraveler(), t.getCreatedByAgent())
                .filter(Objects::nonNull).map(User::getId).distinct())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<UserRowDto> rows = users.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(u -> new UserRowDto(u.getId(), name(u), u.getEmail(), u.getPhone(), u.getRole(),
                        userStatus(u, verification.get(u.getId()), agentActive.get(u.getId())), u.isActive(),
                        u.getCreatedAt(), shipments.getOrDefault(u.getId(), 0L), tripCounts.getOrDefault(u.getId(), 0L)))
                .filter(matches(q, UserRowDto::name, UserRowDto::email, UserRowDto::phone))
                .toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", (long) rows.size());
        for (Role r : Role.values()) counts.put(r.name(), rows.stream().filter(u -> u.role() == r).count());
        List<UserRowDto> filtered = rows.stream().filter(u -> role == null || u.role() == role)
                .filter(u -> blank(status) || u.status().equalsIgnoreCase(status)).toList();
        return paginate(filtered, page, counts);
    }

    static String userStatus(User user, VerificationLevel travelerLevel, Boolean agentProfileActive) {
        if (!user.isActive()) return "SUSPENDED";
        return switch (user.getRole()) {
            case TRAVELER -> travelerLevel != null && travelerLevel != VerificationLevel.UNVERIFIED ? "VERIFIED" : "PENDING";
            case AGENT -> Boolean.TRUE.equals(agentProfileActive) ? "VERIFIED" : "PENDING";
            default -> user.isMustChangePassword() ? "PENDING" : "VERIFIED";
        };
    }

    // ---------- Trips ----------

    public ListDto<TripRowDto> trips(String q, String status, int page) {
        Map<UUID, Long> offerCounts = offers.findAll().stream()
                .collect(Collectors.groupingBy(o -> o.getTrip().getId(), Collectors.counting()));
        LocalDate today = LocalDate.now(clock);
        List<TripRowDto> rows = trips.findAll().stream()
                .sorted(Comparator.comparing(MarketTrip::getDepartureDate).reversed())
                .map(t -> new TripRowDto(t.getId(), t.getTraveler().getId(), name(t.getTraveler()),
                        t.getTraveler().getEmail(), t.getOrigin(), t.getDestination(), t.getDepartureDate(),
                        t.getExpectedArrivalDate(), t.getCapacityKg(), t.getReservedKg(),
                        offerCounts.getOrDefault(t.getId(), 0L), tripStatus(t, today), t.getCreatedByAgent() != null,
                        t.getCreatedAt()))
                .filter(matches(q, TripRowDto::travelerName, TripRowDto::travelerEmail, TripRowDto::origin,
                        TripRowDto::destination))
                .toList();
        return paginate(rows.stream().filter(t -> blank(status) || t.status().equalsIgnoreCase(status)).toList(),
                page, countBy(rows, TripRowDto::status));
    }

    static String tripStatus(MarketTrip trip, LocalDate today) {
        if (trip.getDepartureDate().isAfter(today)) return "UPCOMING";
        LocalDate arrival = trip.getExpectedArrivalDate() == null ? trip.getDepartureDate() : trip.getExpectedArrivalDate();
        return arrival.isBefore(today) ? "COMPLETED" : "IN_PROGRESS";
    }

    // ---------- Requests (shipment requests, packages, bookings, tracking, disputes, reviews) ----------

    public ListDto<RequestRowDto> requests(String view, String q, String status, int page) {
        Set<UUID> reviewed = "reviews".equalsIgnoreCase(view) ? new HashSet<>(reviews.reviewedPackageIds()) : Set.of();
        Predicate<DeliveryRequest> inView = switch (blank(view) ? "all" : view.toLowerCase(Locale.ROOT)) {
            case "all" -> r -> true;
            case "bookings" -> r -> r.getAcceptedOffer() != null;
            case "tracking" -> r -> IN_FLIGHT.contains(r.getStatus());
            case "disputes" -> r -> r.getStatus() == DeliveryStatus.DISPUTED || r.getDisputeReason() != null;
            case "reviews" -> r -> r.getRating() != null || reviewed.contains(r.getId());
            default -> throw new BadRequestException("Unknown view: " + view);
        };
        Map<UUID, Long> offerCounts = offers.findAll().stream()
                .collect(Collectors.groupingBy(o -> o.getRequest().getId(), Collectors.counting()));
        List<DeliveryRequest> rows = requests.findAll().stream().filter(inView)
                .filter(matches(q, r -> senderName(r), r -> senderEmail(r), DeliveryRequest::getPickupCity,
                        DeliveryRequest::getDestinationCity, DeliveryRequest::getDescription, r -> r.getId().toString()))
                .sorted(Comparator.comparing(DeliveryRequest::getUpdatedAt).reversed()).toList();
        Map<String, Long> counts = countBy(rows, r -> r.getStatus().name());
        counts.put("AWAITING_REVIEW", rows.stream().filter(this::awaitingReview).count());
        List<DeliveryRequest> filtered = rows.stream().filter(r -> blank(status)
                || ("AWAITING_REVIEW".equalsIgnoreCase(status) ? awaitingReview(r) : r.getStatus().name().equalsIgnoreCase(status)))
                .toList();
        ListDto<DeliveryRequest> paged = paginate(filtered, page, counts);
        return new ListDto<>(paged.items().stream().map(r -> requestRow(r, offerCounts.getOrDefault(r.getId(), 0L))).toList(),
                paged.page(), paged.size(), paged.totalItems(), paged.totalPages(), counts);
    }

    private RequestRowDto requestRow(DeliveryRequest r, long offerCount) {
        List<DeliveryEvent> timeline = events.findByRequestIdOrderBySequenceNumberAsc(r.getId());
        DeliveryEvent last = timeline.isEmpty() ? null : timeline.get(timeline.size() - 1);
        DeliveryOffer accepted = r.getAcceptedOffer();
        return new RequestRowDto(r.getId(), senderName(r), senderEmail(r), r.getPickupCity(), r.getDestinationCity(),
                r.getItemType(), r.getDescription(), r.getWeightKg(), r.getOfferedPrice(), r.getStatus(),
                r.isAdminApproved(), r.getEscrowStatus(), r.getEscrowAmount(),
                accepted == null ? null : name(accepted.getTraveler()), accepted == null ? null : accepted.getPrice(),
                offerCount, r.getRating(), r.getDisputeReason(), r.getSender() == null, r.getCreatedByAgent() != null,
                r.getDeadline(), last == null ? null : last.getDescription(), last == null ? null : last.getCreatedAt(),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    @Transactional
    public RequestRowDto resolveDispute(UUID id, ResolveDisputeInput input) {
        if (input.outcome() != DeliveryStatus.COMPLETED && input.outcome() != DeliveryStatus.CANCELLED)
            throw new BadRequestException("A dispute resolves to COMPLETED or CANCELLED");
        marketplace.transition(id, new TransitionInput(input.outcome(), input.reason()));
        DeliveryRequest r = requests.findById(id).orElseThrow();
        return requestRow(r, offers.findByRequestIdOrderByCreatedAtAsc(id).size());
    }

    // ---------- Payments ----------

    public PaymentsDto payments(LocalDate from, LocalDate to, String status, String q, int page) {
        Range range = range(from, to);
        PlatformSettings s = currentSettings();
        Map<UUID, Instant> fundedAt = firstEventAt(events.findAll(), DeliveryStatus.PAID_ESCROW);
        List<PaymentRowDto> rows = requests.findAll().stream()
                .filter(r -> (r.getEscrowStatus() == EscrowStatus.DEMO_HELD
                        || r.getEscrowStatus() == EscrowStatus.DEMO_RELEASED
                        || r.getEscrowStatus() == EscrowStatus.DEMO_REFUNDED) && r.getEscrowAmount() != null)
                .map(r -> payment(r, fundedAt.getOrDefault(r.getId(), r.getUpdatedAt()), s))
                .filter(p -> range.contains(p.fundedAt()))
                .filter(matches(q, PaymentRowDto::senderName, PaymentRowDto::travelerName, PaymentRowDto::route))
                .sorted(Comparator.comparing(PaymentRowDto::fundedAt).reversed()).toList();
        BigDecimal held = sum(rows, EscrowStatus.DEMO_HELD), released = sum(rows, EscrowStatus.DEMO_RELEASED),
                refunded = sum(rows, EscrowStatus.DEMO_REFUNDED);
        BigDecimal fees = rows.stream().filter(p -> p.escrowStatus() != EscrowStatus.DEMO_REFUNDED)
                .map(PaymentRowDto::platformFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PaymentRowDto> filtered = rows.stream()
                .filter(p -> blank(status) || p.escrowStatus().name().equalsIgnoreCase(status)).toList();
        return new PaymentsDto(new PaymentSummaryDto(money(held), money(released), money(refunded), money(fees)),
                paginate(filtered, page, countBy(rows, p -> p.escrowStatus().name())));
    }

    static PaymentRowDto payment(DeliveryRequest r, Instant fundedAt, PlatformSettings s) {
        BigDecimal amount = r.getEscrowAmount();
        BigDecimal fee = fee(amount, s.getPlatformFeePercent());
        BigDecimal travelerShare = switch (s.getFeePayer()) {
            case SENDER -> BigDecimal.ZERO;
            case TRAVELER -> fee;
            case SPLIT -> fee.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        };
        DeliveryOffer accepted = r.getAcceptedOffer();
        return new PaymentRowDto(r.getId(), senderName(r), accepted == null ? null : name(accepted.getTraveler()),
                route(r), money(amount), fee, money(amount.subtract(travelerShare)), r.getEscrowStatus(), r.getStatus(),
                fundedAt);
    }

    // ---------- Commissions ----------

    public ListDto<CommissionRowDto> commissions(String status, String q, int page) {
        List<CommissionRowDto> rows = commissions.findAll().stream()
                .sorted(Comparator.comparing(AgentCommission::getCreatedAt).reversed())
                .map(c -> new CommissionRowDto(c.getId(), name(c.getAgent()),
                        c.getCustomer() == null ? null : name(c.getCustomer()), c.getRequest().getId(), route(c.getRequest()),
                        c.getRate(), c.getBaseAmount(), c.getAmount(), c.getStatus(), c.getCreatedAt(), c.getPaidAt()))
                .filter(matches(q, CommissionRowDto::agentName, CommissionRowDto::customerName, CommissionRowDto::route))
                .toList();
        CommissionStatus wanted = blank(status) ? null : parse(CommissionStatus.class, status);
        return paginate(rows.stream().filter(c -> wanted == null || c.status() == wanted).toList(), page,
                countBy(rows, c -> c.status().name()));
    }

    // ---------- Reports ----------

    public ReportDto report(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        List<DeliveryRequest> inRange = requests.findAll().stream().filter(r -> range.contains(r.getCreatedAt())).toList();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (DeliveryStatus s : DeliveryStatus.values())
            byStatus.put(s.name(), inRange.stream().filter(r -> r.getStatus() == s).count());
        Map<String, Long> byRole = new LinkedHashMap<>();
        List<User> allUsers = users.findAll();
        for (Role r : Role.values()) byRole.put(r.name(), allUsers.stream().filter(u -> u.getRole() == r).count());
        List<RouteCountDto> routes = inRange.stream().collect(Collectors.groupingBy(AdminConsoleService::route))
                .entrySet().stream()
                .map(e -> new RouteCountDto(e.getKey(), e.getValue().size(), money(e.getValue().stream()
                        .map(DeliveryRequest::getOfferedPrice).reduce(BigDecimal.ZERO, BigDecimal::add))))
                .sorted(Comparator.comparingLong(RouteCountDto::requests).reversed()).limit(5).toList();
        List<Integer> ratings = inRange.stream().map(DeliveryRequest::getRating).filter(Objects::nonNull).toList();
        BigDecimal avg = ratings.isEmpty() ? null : BigDecimal.valueOf(ratings.stream().mapToInt(i -> i).average().orElse(0))
                .setScale(2, RoundingMode.HALF_UP);
        return new ReportDto(range.from, range.to, byStatus, byRole, routes, avg, ratings.size());
    }

    // ---------- Settings ----------

    public SettingsDto settings() {
        return settingsDto(currentSettings());
    }

    @Transactional
    public SettingsDto updateSettings(SettingsInput input) {
        PlatformSettings s = settings.findById(PlatformSettings.SINGLETON_ID).orElseGet(AdminConsoleService::defaults);
        s.setPlatformFeePercent(input.platformFeePercent().setScale(2, RoundingMode.HALF_UP));
        s.setAgentCommissionPercent(input.agentCommissionPercent().setScale(2, RoundingMode.HALF_UP));
        s.setFeePayer(input.feePayer());
        s.setUpdatedAt(clock.instant());
        s.setUpdatedBy(users.getReferenceById(SecurityUtils.currentUser().userId()));
        return settingsDto(settings.save(s));
    }

    public PlatformSettings currentSettings() {
        return settings.findById(PlatformSettings.SINGLETON_ID).orElseGet(AdminConsoleService::defaults);
    }

    static PlatformSettings defaults() {
        return PlatformSettings.builder().id(PlatformSettings.SINGLETON_ID).platformFeePercent(new BigDecimal("10.00"))
                .agentCommissionPercent(new BigDecimal("10.00")).feePayer(PlatformSettings.FeePayer.SENDER)
                .updatedAt(Instant.EPOCH).build();
    }

    private SettingsDto settingsDto(PlatformSettings s) {
        return new SettingsDto(s.getPlatformFeePercent(), s.getAgentCommissionPercent(), s.getFeePayer(),
                s.getUpdatedAt(), s.getUpdatedBy() == null ? null : name(s.getUpdatedBy()));
    }

    // ---------- Helpers ----------

    private boolean awaitingReview(DeliveryRequest r) {
        return r.getStatus() == DeliveryStatus.REQUESTED && !r.isAdminApproved();
    }

    private static boolean isRevenue(DeliveryRequest r) {
        return r.getEscrowAmount() != null && (r.getEscrowStatus() == EscrowStatus.DEMO_HELD
                || r.getEscrowStatus() == EscrowStatus.DEMO_RELEASED);
    }

    static BigDecimal fee(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<PaymentRowDto> rows, EscrowStatus status) {
        return rows.stream().filter(p -> p.escrowStatus() == status).map(PaymentRowDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<UUID, Instant> firstEventAt(List<DeliveryEvent> all, DeliveryStatus status) {
        return all.stream().filter(e -> e.getStatus() == status)
                .collect(Collectors.toMap(e -> e.getRequest().getId(), DeliveryEvent::getCreatedAt,
                        (a, b) -> a.isBefore(b) ? a : b));
    }

    private Range range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new BadRequestException("'from' must not be after 'to'");
        if (ChronoUnit.DAYS.between(start, end) > 366) throw new BadRequestException("Date range is limited to one year");
        return new Range(start, end);
    }

    private record Range(LocalDate from, LocalDate to) {
        boolean contains(Instant at) {
            LocalDate d = day(at);
            return at != null && !d.isBefore(from) && !d.isAfter(to);
        }
    }

    private static LocalDate day(Instant at) {
        return at == null ? LocalDate.MIN : at.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static LocalDate bucket(LocalDate d, boolean weekly) {
        return weekly ? d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : d;
    }

    @SafeVarargs
    private static <T> Predicate<T> matches(String q, Function<T, String>... fields) {
        if (blank(q)) return t -> true;
        String needle = q.trim().toLowerCase(Locale.ROOT);
        return t -> Arrays.stream(fields).map(f -> f.apply(t))
                .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static <T> Map<String, Long> countBy(List<T> rows, Function<T, String> key) {
        return rows.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
    }

    static <T> ListDto<T> paginate(List<T> rows, int page, Map<String, Long> counts) {
        int totalPages = Math.max(1, (int) Math.ceil(rows.size() / (double) PAGE_SIZE));
        int p = Math.min(Math.max(page, 0), totalPages - 1);
        List<T> items = rows.subList(Math.min(p * PAGE_SIZE, rows.size()), Math.min((p + 1) * PAGE_SIZE, rows.size()));
        return new ListDto<>(List.copyOf(items), p, PAGE_SIZE, rows.size(), totalPages, counts);
    }

    private static Role parseRole(String type) {
        if (blank(type) || type.equalsIgnoreCase("ALL")) return null;
        String t = type.toUpperCase(Locale.ROOT);
        return parse(Role.class, t.endsWith("S") ? t.substring(0, t.length() - 1) : t);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown value: " + value);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    static String name(User u) {
        if (u == null) return null;
        String full = Stream.of(u.getFirstName(), u.getLastName()).filter(s -> !blank(s))
                .collect(Collectors.joining(" "));
        return full.isEmpty() ? u.getEmail() : full;
    }

    private static String senderName(DeliveryRequest r) {
        return r.getSender() != null ? name(r.getSender()) : r.getGuestName();
    }

    private static String senderEmail(DeliveryRequest r) {
        return r.getSender() != null ? r.getSender().getEmail() : r.getGuestEmail();
    }

    private static String route(DeliveryRequest r) {
        return r.getPickupCity() + " → " + r.getDestinationCity();
    }

    private static String label(String value) {
        String s = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
