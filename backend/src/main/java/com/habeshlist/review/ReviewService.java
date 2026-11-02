package com.habeshlist.review;

import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.*;
import com.habeshlist.repository.DeliveryRequestRepository;
import com.habeshlist.review.DeliveryReview.*;
import com.habeshlist.review.ReviewDtos.*;
import com.habeshlist.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service
public class ReviewService {
    private static final Set<Criterion> CARRIER_SCORES = Set.of(Criterion.COMMUNICATION,
            Criterion.ON_TIME_DELIVERY, Criterion.PACKAGE_HANDLING, Criterion.RELIABILITY);
    private static final Set<Criterion> SENDER_SCORES = Set.of(Criterion.COMMUNICATION,
            Criterion.ACCURATE_DESCRIPTION, Criterion.HANDOFF_TIMELINESS, Criterion.PACKAGING_QUALITY);
    private final DeliveryRequestRepository packages;
    private final DeliveryReviewRepository reviews;
    private final Clock clock;

    public ReviewService(DeliveryRequestRepository packages, DeliveryReviewRepository reviews, Clock clock) {
        this.packages = packages;
        this.reviews = reviews;
        this.clock = clock;
    }

    private boolean sender(DeliveryRequest p) {
        return p.getSender() != null && p.getSender().getId().equals(SecurityUtils.currentUser().userId());
    }
    private boolean carrier(DeliveryRequest p) {
        return p.getAcceptedOffer() != null
                && p.getAcceptedOffer().getTraveler().getId().equals(SecurityUtils.currentUser().userId());
    }
    private Direction direction(DeliveryRequest p) {
        Role role = SecurityUtils.currentUser().role();
        if (role == Role.SENDER && sender(p)) return Direction.SENDER_TO_CARRIER;
        if ((role == Role.TRAVELER || role == Role.AGENT) && carrier(p)) return Direction.CARRIER_TO_SENDER;
        return null;
    }
    private boolean completed(DeliveryRequest p) {
        return p.getStatus() == DeliveryStatus.COMPLETED && p.getSender() != null && p.getAcceptedOffer() != null
                && !p.getSender().getId().equals(p.getAcceptedOffer().getTraveler().getId());
    }
    private void visible(DeliveryRequest p) {
        if (SecurityUtils.currentUser().role() != Role.ADMIN && !sender(p) && !carrier(p))
            throw new AccessDeniedException("Reviews are private to the booking participants and administrators");
        if (SecurityUtils.currentUser().role() == Role.TRAVELER && !p.isAdminApproved() && !completed(p))
            throw new AccessDeniedException("The traveler review exception applies only after confirmed completion");
    }

    @Transactional(readOnly = true)
    public BookingDto forPackage(UUID id) {
        DeliveryRequest p = packages.findById(id).orElseThrow(() -> new NotFoundException("Package not found"));
        visible(p);
        return dto(p);
    }

    @Transactional(readOnly = true)
    public InboxDto inbox(int page) {
        if (page < 0) throw new BadRequestException("Page must be zero or greater");
        var result = packages.completedForReview(SecurityUtils.currentUser().userId(), PageRequest.of(page, 20));
        return new InboxDto(result.map(this::dto).getContent(), page, result.getTotalPages(), result.getTotalElements());
    }

    @Transactional
    public BookingDto submit(UUID id, Input input) {
        DeliveryRequest p = packages.findForUpdate(id).orElseThrow(() -> new NotFoundException("Package not found"));
        Direction direction = direction(p);
        if (direction == null) throw new AccessDeniedException("Only a participating sender, traveler or agent can review");
        if (!completed(p)) throw new BadRequestException("Confirm delivery completion before submitting a review");
        if (reviews.existsByPackageRequestIdAndDirection(id, direction)
                || (direction == Direction.SENDER_TO_CARRIER && p.getRating() != null))
            throw new BadRequestException("You have already reviewed this delivery");
        Set<Criterion> expected = direction == Direction.SENDER_TO_CARRIER ? CARRIER_SCORES : SENDER_SCORES;
        if (!input.scores().keySet().equals(expected))
            throw new BadRequestException("Choose all four rating categories for the person you are reviewing");
        save(p, direction, input.rating(), input.scores(), input.comment(), false);
        return dto(p);
    }

    @Transactional
    public void submitLegacy(UUID id, int rating) {
        DeliveryRequest p = packages.findForUpdate(id).orElseThrow(() -> new NotFoundException("Package not found"));
        if (direction(p) != Direction.SENDER_TO_CARRIER) throw new AccessDeniedException("Only the sender can rate this delivery");
        if (!completed(p) || p.getRating() != null
                || reviews.existsByPackageRequestIdAndDirection(id, Direction.SENDER_TO_CARRIER))
            throw new BadRequestException("Rate a completed delivery once");
        if (rating < 1 || rating > 5) throw new BadRequestException("Choose a rating from 1 to 5");
        save(p, Direction.SENDER_TO_CARRIER, rating, Map.of(), null, true);
    }

    private void save(DeliveryRequest p, Direction direction, int rating, Map<Criterion, Integer> scores,
            String comment, boolean legacy) {
        boolean fromSender = direction == Direction.SENDER_TO_CARRIER;
        User carrier = p.getAcceptedOffer().getTraveler();
        reviews.saveAndFlush(DeliveryReview.builder().packageRequest(p)
                .author(fromSender ? p.getSender() : carrier).recipient(fromSender ? carrier : p.getSender())
                .direction(direction).rating(rating).scores(new HashMap<>(scores))
                .comment(comment == null || comment.isBlank() ? null : comment.strip())
                .legacy(legacy).createdAt(clock.instant()).build());
        // Preserve the established carrier averages/counts and ranking without counting a review twice.
        if (fromSender) p.setRating(rating);
    }

    private BookingDto dto(DeliveryRequest p) {
        Direction direction = direction(p);
        List<ReviewDto> rows = reviews.findByPackageRequestIdOrderByCreatedAtAscIdAsc(p.getId()).stream()
                .map(r -> new ReviewDto(r.getId(), r.getDirection(), r.getRating(), Map.copyOf(r.getScores()),
                        r.getComment(), r.isLegacy(), r.getCreatedAt(),
                        r.getAuthor().getId().equals(SecurityUtils.currentUser().userId()))).toList();
        boolean canReview = completed(p) && direction != null
                && rows.stream().noneMatch(r -> r.direction() == direction)
                && (direction != Direction.SENDER_TO_CARRIER || p.getRating() == null);
        return new BookingDto(p.getId(), p.getPickupCity(), p.getDestinationCity(), p.getStatus(), canReview, direction, rows);
    }
}
