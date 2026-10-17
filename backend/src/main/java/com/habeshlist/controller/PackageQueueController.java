package com.habeshlist.controller;

import com.habeshlist.dto.PackageQueueDtos;
import com.habeshlist.service.PackageNotificationService;
import com.habeshlist.service.PackageQueueService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/package-queue")
public class PackageQueueController {
    private final PackageQueueService queue;
    private final PackageNotificationService notifications;

    public PackageQueueController(PackageQueueService queue, PackageNotificationService notifications) {
        this.queue = queue;
        this.notifications = notifications;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PackageQueueDtos.PackageDto> list() { return queue.list(); }

    @GetMapping("/{id}/payment-proof")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','TRAVELER')")
    public Map<String, String> paymentProof(@PathVariable UUID id) { return Map.of("proof", queue.paymentProof(id)); }

    @GetMapping("/{id}/sender")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','TRAVELER')")
    public PackageQueueDtos.SenderContactDto sender(@PathVariable UUID id) { return queue.senderContact(id); }

    @GetMapping("/notifications")
    public List<PackageQueueDtos.NotificationDto> notifications() { return notifications.list(); }

    @PutMapping("/notifications/{id}/read")
    public void markRead(@PathVariable UUID id) { notifications.markRead(id); }
}
