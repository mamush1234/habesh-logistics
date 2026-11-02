package com.habeshlist.agent;

import com.habeshlist.agent.AgentTypes.AuthorizationStatus;
import com.habeshlist.entity.AgentProfile;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.AgentProfileRepository;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

/** Enforces agent ownership and on-behalf authorization rules and writes the audit trail. */
@Service
public class AgentAccessService {
    private final UserRepository users;
    private final AgentProfileRepository profiles;
    private final AgentCustomerRepository customers;
    private final AgentAuthorizationRepository authorizations;
    private final AgentAuditLogRepository audit;
    private final Clock clock;

    public AgentAccessService(UserRepository users, AgentProfileRepository profiles, AgentCustomerRepository customers,
            AgentAuthorizationRepository authorizations, AgentAuditLogRepository audit, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.customers = customers;
        this.authorizations = authorizations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public User currentAgent() {
        var principal = SecurityUtils.currentUser();
        if (principal.role() != Role.AGENT) throw new AccessDeniedException("Agent access required");
        User agent = users.findById(principal.userId()).orElseThrow(() -> new NotFoundException("User not found"));
        AgentProfile profile = profiles.findById(agent.getId()).orElse(null);
        if (!agent.isActive() || agent.getRole() != Role.AGENT || profile == null || !profile.isActive())
            throw new AccessDeniedException("Your agent account is not active");
        return agent;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AgentCustomer ownedCustomer(User agent, UUID customerId) {
        return customers.findByAgentIdAndCustomerId(agent.getId(), customerId)
                .orElseThrow(() -> new AccessDeniedException("This customer is not managed by your agent account"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AgentAuthorization requireActiveAuthorization(User agent, User customer) {
        var now = clock.instant();
        return authorizations.findByAgentIdAndCustomerIdAndStatusOrderByExpiresAtDesc(
                        agent.getId(), customer.getId(), AuthorizationStatus.ACTIVE).stream()
                .filter(a -> a.activeAt(now)).findFirst()
                .orElseThrow(() -> new AccessDeniedException(
                        "An active, unexpired customer authorization is required for this action"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AgentAuditLog record(User agent, User customer, AgentAuthorization authorization, String action,
            String targetType, UUID targetId, String details) {
        return audit.save(AgentAuditLog.builder().agent(agent).customer(customer).authorization(authorization)
                .action(action).targetType(targetType).targetId(targetId)
                .details(details == null ? null : details.length() > 500 ? details.substring(0, 500) : details)
                .createdAt(clock.instant()).build());
    }
}
