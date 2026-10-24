package com.habeshlist.service;

import com.habeshlist.dto.GuestRequestDtos.*;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import com.habeshlist.exception.*;
import com.habeshlist.repository.*;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.util.GuestProductPhoto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class GuestRequestService {
    private static final String RECEIPT = "Your request has been received. We will contact you once we find a traveler.";
    private final GuestRequestLinkRepository links;
    private final DeliveryRequestRepository requests;
    private final DeliveryEventRepository events;
    private final UserRepository users;
    private final PackageNotificationService notifications;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public GuestRequestService(GuestRequestLinkRepository links, DeliveryRequestRepository requests,
            DeliveryEventRepository events, UserRepository users, PackageNotificationService notifications, Clock clock) {
        this.links = links;
        this.requests = requests;
        this.events = events;
        this.users = users;
        this.notifications = notifications;
        this.clock = clock;
    }

    private String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new NotFoundException("Guest link not found");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private String status(GuestRequestLink link) {
        if (link.getRequest() != null) return "SUBMITTED";
        if (link.getRevokedAt() != null) return "REVOKED";
        return clock.instant().isBefore(link.getExpiresAt()) ? "OPEN" : "EXPIRED";
    }
    private LinkDto dto(GuestRequestLink link) {
        return new LinkDto(link.getId(), link.getLabel(), link.getCreatedAt(), link.getExpiresAt(), status(link),
                link.getRequest() == null ? null : link.getRequest().getId());
    }
    private GuestStatusDto guestStatus(GuestRequestLink link) {
        String state = status(link);
        return new GuestStatusDto(state, switch (state) {
            case "OPEN" -> "Submit one package request using this private link.";
            case "SUBMITTED" -> RECEIPT;
            default -> "This guest link is no longer available. Please contact us for a new link.";
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CreatedLinkDto create(CreateLinkInput input) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        User creator = users.findById(SecurityUtils.currentUser().userId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        GuestRequestLink link = links.save(GuestRequestLink.builder().label(input.label().strip()).tokenHash(hash(token))
                .createdBy(creator).createdAt(clock.instant()).expiresAt(clock.instant().plus(Duration.ofDays(7))).build());
        return new CreatedLinkDto(dto(link), token);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<LinkDto> list() { return links.findAllByOrderByCreatedAtDesc().stream().map(this::dto).toList(); }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LinkDto revoke(UUID id) {
        GuestRequestLink link = links.findForUpdate(id).orElseThrow(() -> new NotFoundException("Guest link not found"));
        if (link.getRequest() != null) throw new BadRequestException("This link has already been used; manage its request instead");
        link.setRevokedAt(clock.instant());
        return dto(link);
    }
    @Transactional
    public GuestStatusDto check(TokenInput input) {
        return guestStatus(links.findByTokenHashForUpdate(hash(input.token()))
                .orElseThrow(() -> new NotFoundException("Guest link not found")));
    }
    @Transactional
    public GuestStatusDto submit(SubmitInput input) {
        GuestRequestLink link = links.findByTokenHashForUpdate(hash(input.token()))
                .orElseThrow(() -> new NotFoundException("Guest link not found"));
        if (link.getRequest() != null) return guestStatus(link);
        if (!status(link).equals("OPEN")) throw new BadRequestException("This guest link has expired or was revoked. Contact us for a new link.");
        var data = input.request();
        if (data.availableFrom().isBefore(LocalDate.now(clock.withZone(ZoneId.systemDefault())))
                || data.deadline().isBefore(data.availableFrom()))
            throw new BadRequestException("Choose an available date today or later and a deadline on or after that date");
        String photo = GuestProductPhoto.prepare(input.photoDataUrl());
        DeliveryRequest request = requests.save(DeliveryRequest.builder()
                .guestName(input.name().strip()).guestEmail(input.email().strip()).guestPhone(input.phone().strip())
                .productPhoto(photo).pickupCity(data.pickupCity().strip()).destinationCity(data.destinationCity().strip())
                .availableFrom(data.availableFrom()).deadline(data.deadline()).itemType(data.itemType())
                .description(data.description().strip()).weightKg(data.weightKg()).offeredPrice(data.offeredPrice())
                .status(DeliveryStatus.REQUESTED).createdAt(clock.instant()).updatedAt(clock.instant()).build());
        events.save(DeliveryEvent.builder().request(request).status(DeliveryStatus.REQUESTED)
                .actorId(link.getCreatedBy().getId()).description("Guest request submitted; admin will coordinate with the sender")
                .createdAt(clock.instant()).sequenceNumber(0).build());
        link.setRequest(request);
        notifications.broadcastRequest(request);
        return guestStatus(link);
    }
}
