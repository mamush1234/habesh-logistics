package com.habeshlist.repository;

import com.habeshlist.entity.Luggage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LuggageRepository extends JpaRepository<Luggage, UUID> {
    Optional<Luggage> findByLuggageNumber(String luggageNumber);
    List<Luggage> findAllByTripId(UUID tripId);
}
