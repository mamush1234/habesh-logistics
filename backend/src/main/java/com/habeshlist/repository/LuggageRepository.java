package com.habeshlist.repository;

import com.habeshlist.entity.Luggage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LuggageRepository extends JpaRepository<Luggage, UUID> {
    Optional<Luggage> findByLuggageNumber(String luggageNumber);
    List<Luggage> findAllByTripId(UUID tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Luggage l where l.id = :id")
    Optional<Luggage> findForUpdate(UUID id);
}
