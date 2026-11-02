package com.habeshlist.agent;

import com.habeshlist.agent.AgentTypes.AuthorizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AgentAuthorizationRepository extends JpaRepository<AgentAuthorization, UUID> {
    List<AgentAuthorization> findByAgentIdOrderByGrantedAtDesc(UUID agentId);
    List<AgentAuthorization> findByAgentIdAndCustomerIdAndStatusOrderByExpiresAtDesc(
            UUID agentId, UUID customerId, AuthorizationStatus status);
}
