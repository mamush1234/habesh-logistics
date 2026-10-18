package com.habeshlist.repository;

import com.habeshlist.entity.DeliveryMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DeliveryMessageRepository extends JpaRepository<DeliveryMessage, UUID> {
    Optional<DeliveryMessage> findTopByRequestIdOrderBySequenceNumberDesc(UUID requestId);
    List<DeliveryMessage> findByRequestIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
            UUID requestId, long before, Pageable page);
}
