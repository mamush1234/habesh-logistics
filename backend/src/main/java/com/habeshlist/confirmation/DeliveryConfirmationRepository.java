package com.habeshlist.confirmation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DeliveryConfirmationRepository extends JpaRepository<DeliveryConfirmation, UUID> {}
