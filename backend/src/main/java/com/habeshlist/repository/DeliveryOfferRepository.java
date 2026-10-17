package com.habeshlist.repository;

import com.habeshlist.entity.DeliveryOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DeliveryOfferRepository extends JpaRepository<DeliveryOffer, UUID> {
    List<DeliveryOffer> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
    Optional<DeliveryOffer> findByRequestIdAndTravelerId(UUID requestId, UUID travelerId);
}
