package com.habeshlist.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AgentAuditLogRepository extends JpaRepository<AgentAuditLog, UUID> {
    List<AgentAuditLog> findTop10ByAgentIdOrderByCreatedAtDesc(UUID agentId);
    List<AgentAuditLog> findByAgentIdOrderByCreatedAtDesc(UUID agentId);
}
