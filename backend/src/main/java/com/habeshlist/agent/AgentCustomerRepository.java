package com.habeshlist.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AgentCustomerRepository extends JpaRepository<AgentCustomer, UUID> {
    List<AgentCustomer> findByAgentIdOrderByCreatedAtDesc(UUID agentId);
    Optional<AgentCustomer> findByAgentIdAndCustomerId(UUID agentId, UUID customerId);
}
