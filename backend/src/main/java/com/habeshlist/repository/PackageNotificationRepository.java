package com.habeshlist.repository;

import com.habeshlist.entity.PackageNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PackageNotificationRepository extends JpaRepository<PackageNotification, UUID> {
    List<PackageNotification> findTop100ByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    void deleteAllByShipmentId(UUID shipmentId);
}
