package com.habeshlist.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StripePaymentRepository extends JpaRepository<StripePayment, UUID> {
    Optional<StripePayment> findByPackageRequestId(UUID id);
    Optional<StripePayment> findByIntentId(String id);
    org.springframework.data.domain.Page<StripePayment> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);
}
