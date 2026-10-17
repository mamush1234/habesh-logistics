package com.habeshlist.repository;

import com.habeshlist.entity.DeliveryRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface DeliveryRequestRepository extends JpaRepository<DeliveryRequest, UUID> {
    List<DeliveryRequest> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DeliveryRequest r where r.id = :id")
    Optional<DeliveryRequest> findForUpdate(UUID id);
    @Query("select avg(r.rating) from DeliveryRequest r where r.acceptedOffer.traveler.id = :travelerId and r.rating is not null")
    Double averageRating(UUID travelerId);
    @Query("select count(r) from DeliveryRequest r where r.acceptedOffer.traveler.id = :travelerId and r.rating is not null")
    long ratingCount(UUID travelerId);
}
