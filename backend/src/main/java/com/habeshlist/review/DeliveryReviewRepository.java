package com.habeshlist.review;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DeliveryReviewRepository extends JpaRepository<DeliveryReview, UUID> {
    @org.springframework.data.jpa.repository.Query("select distinct r.packageRequest.id from DeliveryReview r")
    List<UUID> reviewedPackageIds();
    List<DeliveryReview> findByPackageRequestIdOrderByCreatedAtAscIdAsc(UUID id);
    boolean existsByPackageRequestIdAndDirection(UUID id, DeliveryReview.Direction direction);
}
