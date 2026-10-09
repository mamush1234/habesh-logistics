package com.habeshlist.repository;

import com.habeshlist.entity.AgentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AgentProfileRepository extends JpaRepository<AgentProfile, UUID> {
    Optional<AgentProfile> findByReferralCode(String referralCode);
}
