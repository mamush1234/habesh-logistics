package com.habeshlist.repository;

import com.habeshlist.entity.Shipment;
import com.habeshlist.entity.enums.ShipmentStatus;
import com.habeshlist.entity.enums.TravelBookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

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
    List<Shipment> findAllByTripId(UUID tripId);
    @Query("select s from Shipment s left join s.luggage l where s.trip.id = :tripId or l.trip.id = :tripId")
    List<Shipment> findAllOnTrip(UUID tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s where s.id = :id")
    Optional<Shipment> findForUpdate(UUID id);

    List<Shipment> findAllByLuggageIsNullAndStatusIn(List<ShipmentStatus> statuses);
    long countByStatus(ShipmentStatus status);

    @Query("select coalesce(sum(s.declaredWeightKg), 0) from Shipment s "
            + "where s.travelAnnouncement.id = :announcementId and s.travelBookingStatus = :status")
    java.math.BigDecimal travelWeight(UUID announcementId, TravelBookingStatus status);

    @Query("select s from Shipment s join fetch s.travelAnnouncement a left join fetch s.sender "
            + "where (:authorId is null or a.postedBy.id = :authorId) order by s.createdAt desc")
    List<Shipment> findTravelRequests(UUID authorId);

    @Query("select coalesce(sum(s.totalAmount),0) from Shipment s")
    java.math.BigDecimal totalRevenue();
}
