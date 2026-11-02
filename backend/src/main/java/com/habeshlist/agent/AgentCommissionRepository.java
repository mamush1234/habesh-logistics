package com.habeshlist.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

public interface AgentCommissionRepository extends JpaRepository<AgentCommission, UUID> {
    boolean existsByRequestId(UUID requestId);
    Optional<AgentCommission> findByRequestId(UUID requestId);
    List<AgentCommission> findByAgentIdOrderByCreatedAtDesc(UUID agentId);
    List<AgentCommission> findByAgentIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID agentId, Instant from, Instant to);
}
