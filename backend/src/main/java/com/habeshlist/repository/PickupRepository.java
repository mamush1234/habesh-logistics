package com.habeshlist.repository;

import com.habeshlist.entity.Pickup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PickupRepository extends JpaRepository<Pickup, UUID> {
    Optional<Pickup> findByShipmentId(UUID shipmentId);
}
