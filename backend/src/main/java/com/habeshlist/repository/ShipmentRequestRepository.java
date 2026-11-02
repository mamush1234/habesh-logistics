package com.habeshlist.repository;

import com.habeshlist.entity.ShipmentRequest;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public interface ShipmentRequestRepository extends JpaRepository<ShipmentRequest, UUID> {
    Optional<ShipmentRequest> findByPendingPackageId(UUID packageId);
    List<ShipmentRequest> findByPackageRequestIdOrderByCreatedAtDesc(UUID packageId);
    boolean existsByAcceptedOfferId(UUID offerId);
    Optional<ShipmentRequest> findByAcceptedOfferId(UUID offerId);
    interface RequestReference {
        UUID getPackageId();
        UUID getTripId();
    }
    @Query("select s.packageRequest.id as packageId, s.trip.id as tripId from ShipmentRequest s where s.id = :id")
    Optional<RequestReference> reference(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShipmentRequest s where s.id = :id")
    Optional<ShipmentRequest> findForUpdate(UUID id);
    @Query("""
        select s from ShipmentRequest s
        where (:admin = true or s.sender.id = :actorId or (:agent = true and s.trip.traveler.id = :actorId))
        order by s.createdAt desc, s.id
        """)
    List<ShipmentRequest> inbox(UUID actorId, boolean admin, boolean agent);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update ShipmentRequest s set s.status = com.habeshlist.entity.ShipmentRequest.Status.EXPIRED,
            s.pendingPackageId = null
        where s.status = com.habeshlist.entity.ShipmentRequest.Status.PENDING
          and (s.expiresAt <= :now or s.packageRequest.status <> com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus.REQUESTED
            or s.trip.departureDate < :today or s.trip.traveler.active = false)
        """)
    int expirePending(Instant now, LocalDate today);
}
