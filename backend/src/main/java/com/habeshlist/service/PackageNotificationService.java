package com.habeshlist.service;

import com.habeshlist.dto.PackageQueueDtos;
import com.habeshlist.entity.PackageNotification;
import com.habeshlist.entity.DeliveryRequest;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.PackageNotificationRepository;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class PackageNotificationService {
    private final PackageNotificationRepository notifications;
    private final UserRepository users;
    private final Clock clock;

    public PackageNotificationService(PackageNotificationRepository notifications, UserRepository users, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    public void broadcastRequest(DeliveryRequest request) {
        Stream.of(Role.ADMIN, Role.AGENT)
                .flatMap(role -> users.findAllByRole(role).stream()).filter(User::isActive)
                .forEach(user -> notifyRequest(request, user, "A delivery request is available: "
                        + request.getPickupCity() + " to " + request.getDestinationCity() + ". Browse marketplace matches."));
    }

    public void notifyRequest(DeliveryRequest request, User user, String message) {
        if (user.getRole() == Role.TRAVELER && !request.isAdminApproved()) return;
        if (user.isActive()) notifications.save(PackageNotification.builder().recipient(user).request(request)
                .message(message).createdAt(clock.instant()).build());
    }

    public void notifyTravelersOfApproval(DeliveryRequest request) {
        users.findAllByRole(Role.TRAVELER).forEach(user -> notifyRequest(request, user,
                "An admin-approved sender request is available to view: " + request.getPickupCity()
                        + " to " + request.getDestinationCity() + ". Traveler access is read-only."));
    }

    public void notifyAdmins(DeliveryRequest request, String message) {
        users.findAllByRole(Role.ADMIN).forEach(user -> notifyRequest(request, user, message));
    }

    @Transactional(readOnly = true)
    public List<PackageQueueDtos.NotificationDto> list() {
        return notifications.findTop100ByRecipientIdOrderByCreatedAtDesc(SecurityUtils.currentUser().userId())
                .stream().filter(n -> SecurityUtils.currentUser().role() != Role.TRAVELER
                        || n.getRequest() == null || n.getRequest().isAdminApproved())
                .map(n -> new PackageQueueDtos.NotificationDto(n.getId(),
                        n.getShipment() == null ? null : n.getShipment().getId(),
                        n.getRequest() == null ? null : n.getRequest().getId(),
                        n.getMessage(), n.getCreatedAt(), n.getReadAt())).toList();
    }

    @Transactional
    public void markRead(UUID id) {
        var notification = notifications.findById(id).orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!notification.getRecipient().getId().equals(SecurityUtils.currentUser().userId())) {
            throw new AccessDeniedException("Not your notification");
        }
        if (SecurityUtils.currentUser().role() == Role.TRAVELER && notification.getRequest() != null
                && !notification.getRequest().isAdminApproved())
            throw new AccessDeniedException("This request is awaiting administrator approval");
        if (notification.getReadAt() == null) notification.setReadAt(clock.instant());
    }
}
