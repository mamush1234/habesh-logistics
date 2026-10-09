package com.habeshlist.repository;

import com.habeshlist.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    Optional<Trip> findByTripNumber(String tripNumber);
    List<Trip> findAllByTravelerId(UUID travelerId);
}
