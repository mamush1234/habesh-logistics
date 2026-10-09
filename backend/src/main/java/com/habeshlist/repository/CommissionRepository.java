package com.habeshlist.repository;

import com.habeshlist.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CommissionRepository extends JpaRepository<Commission, UUID> {
    List<Commission> findAllByAgentId(UUID agentId);
}
