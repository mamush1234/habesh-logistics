package com.habeshlist.repository;

import com.habeshlist.entity.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, UUID> {
    List<DeliveryEvent> findByRequestIdOrderBySequenceNumberAsc(UUID requestId);
    long countByRequestId(UUID requestId);
}
