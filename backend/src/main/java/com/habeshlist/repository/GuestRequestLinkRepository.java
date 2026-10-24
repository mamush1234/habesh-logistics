package com.habeshlist.repository;

import com.habeshlist.entity.GuestRequestLink;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface GuestRequestLinkRepository extends JpaRepository<GuestRequestLink, UUID> {
    List<GuestRequestLink> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from GuestRequestLink l where l.tokenHash = :hash")
    Optional<GuestRequestLink> findByTokenHashForUpdate(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from GuestRequestLink l where l.id = :id")
    Optional<GuestRequestLink> findForUpdate(UUID id);
}
