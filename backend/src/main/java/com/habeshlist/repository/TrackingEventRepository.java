package com.habeshlist.repository;

import com.habeshlist.entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {
    List<TrackingEvent> findAllByShipmentIdOrderByCreatedAtAsc(UUID shipmentId);
}
