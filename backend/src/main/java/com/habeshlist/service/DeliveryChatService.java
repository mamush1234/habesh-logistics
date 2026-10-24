package com.habeshlist.service;

import com.habeshlist.dto.MarketplaceDtos.*;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.*;
import com.habeshlist.repository.*;
import com.habeshlist.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service
public class DeliveryChatService {
    private static final int PAGE_SIZE = 50;
    private final DeliveryRequestRepository requests;
    private final DeliveryMessageRepository messages;
    private final UserRepository users;
    private final PackageNotificationService notifications;
    private final Clock clock;

    public DeliveryChatService(DeliveryRequestRepository requests, DeliveryMessageRepository messages,
            UserRepository users, PackageNotificationService notifications, Clock clock) {
        this.requests = requests;
        this.messages = messages;
        this.users = users;
        this.notifications = notifications;
        this.clock = clock;
    }

    private boolean participant(DeliveryRequest request) {
        UUID id = SecurityUtils.currentUser().userId();
        return (request.getSender() != null && request.getSender().getId().equals(id))
                || (request.getAcceptedOffer() != null && request.getAcceptedOffer().getTraveler().getId().equals(id));
    }
    private void checkAccess(DeliveryRequest request) {
        if (SecurityUtils.currentUser().role() == Role.TRAVELER && !request.isAdminApproved())
            throw new AccessDeniedException("This request is awaiting administrator approval");
        if (request.getSender() == null)
            throw new AccessDeniedException("Guest requests are coordinated directly by an administrator; package chat is unavailable");
        if (request.getAcceptedOffer() == null
                || (!participant(request) && SecurityUtils.currentUser().role() != Role.ADMIN))
            throw new AccessDeniedException("Chat is only available to the matched participants and admins");
    }
    private boolean writable(DeliveryRequest request) {
        return request.getSender() != null && participant(request) && SecurityUtils.currentUser().role() != Role.ADMIN
                && SecurityUtils.currentUser().role() != Role.TRAVELER
                && request.getStatus() != DeliveryStatus.COMPLETED && request.getStatus() != DeliveryStatus.CANCELLED;
    }
    private MessageDto dto(DeliveryMessage message) {
        User author = message.getAuthor();
        return new MessageDto(message.getId(), author.getId(), author.getFirstName() + " " + author.getLastName(),
                author.getRole(), message.getBody(), message.getCreatedAt(), message.getSequenceNumber());
    }

    @Transactional(readOnly = true)
    public ChatDto list(UUID id, Long before) {
        DeliveryRequest request = requests.findById(id).orElseThrow(() -> new NotFoundException("Request not found"));
        checkAccess(request);
        if (before != null && before <= 0) throw new BadRequestException("Message cursor must be positive");
        List<DeliveryMessage> page = messages.findByRequestIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
                id, before == null ? Long.MAX_VALUE : before, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasMore = page.size() > PAGE_SIZE;
        List<DeliveryMessage> visible = page.stream().limit(PAGE_SIZE).toList();
        return new ChatDto(visible.stream().map(this::dto).toList(),
                hasMore ? visible.get(visible.size() - 1).getSequenceNumber() : null, writable(request));
    }

    @Transactional
    public MessageDto send(UUID id, MessageInput input) {
        if (SecurityUtils.currentUser().role() == Role.TRAVELER)
            throw new AccessDeniedException("Travelers have read-only access to approved sender requests");
        DeliveryRequest request = requests.findForUpdate(id).orElseThrow(() -> new NotFoundException("Request not found"));
        checkAccess(request);
        if (!participant(request) || SecurityUtils.currentUser().role() == Role.ADMIN)
            throw new AccessDeniedException("Admins can review chat but cannot send messages");
        if (!writable(request)) throw new BadRequestException("Chat is read-only after completion or cancellation");
        if (input.body() == null || input.body().isBlank() || input.body().length() > 2000)
            throw new BadRequestException("Write a message between 1 and 2000 characters");
        User author = users.findById(SecurityUtils.currentUser().userId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        long sequence = messages.findTopByRequestIdOrderBySequenceNumberDesc(id)
                .map(message -> message.getSequenceNumber() + 1).orElse(1L);
        DeliveryMessage message = messages.saveAndFlush(DeliveryMessage.builder().request(request).author(author)
                .body(input.body().strip()).createdAt(clock.instant()).sequenceNumber(sequence).build());
        User recipient = request.getSender().getId().equals(author.getId())
                ? request.getAcceptedOffer().getTraveler() : request.getSender();
        notifications.notifyRequest(request, recipient, "New package chat message. Open the accepted package to reply.");
        return dto(message);
    }
}
