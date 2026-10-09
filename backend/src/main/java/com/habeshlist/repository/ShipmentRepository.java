package com.habeshlist.repository;

import com.habeshlist.entity.Shipment;
import com.habeshlist.entity.enums.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByTrackingNumber(String trackingNumber);
    Optional<Shipment> findByTrackingToken(String token);
    List<Shipment> findAllBySenderId(UUID senderId);
    List<Shipment> findAllByReferringAgentId(UUID agentId);
    List<Shipment> findAllByAssignedAgentId(UUID agentId);
    List<Shipment> findAllByAssignedTravelerId(UUID travelerId);
    List<Shipment> findAllByLuggageId(UUID luggageId);
    List<Shipment> findAllByLuggageIsNullAndStatusIn(List<ShipmentStatus> statuses);
    long countByStatus(ShipmentStatus status);

    @Query("select coalesce(sum(s.totalAmount),0) from Shipment s")
    java.math.BigDecimal totalRevenue();
}
