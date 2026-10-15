package com.habeshlist.repository;

import com.habeshlist.entity.TravelAnnouncement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface TravelAnnouncementRepository extends JpaRepository<TravelAnnouncement, UUID> {
    @EntityGraph(attributePaths = "postedBy")
    List<TravelAnnouncement> findAllByOrderByDepartureDateAscCreatedAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TravelAnnouncement a where a.id = :id")
    Optional<TravelAnnouncement> findForUpdate(UUID id);
}
