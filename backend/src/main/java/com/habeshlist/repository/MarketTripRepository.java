package com.habeshlist.repository;

import com.habeshlist.entity.MarketTrip;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface MarketTripRepository extends JpaRepository<MarketTrip, UUID> {
    List<MarketTrip> findAllByOrderByDepartureDateAscCreatedAtAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from MarketTrip t where t.id = :id")
    Optional<MarketTrip> findForUpdate(UUID id);
}
