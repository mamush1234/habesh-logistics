package com.habeshlist.agent;

import com.habeshlist.agent.AgentDtos.AgentSummaryDto;
import com.habeshlist.agent.AgentDtos.PromoteInput;
import com.habeshlist.entity.AgentProfile;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.CommissionStatus;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.AgentProfileRepository;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.util.CodeGenerator;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.Year;
import java.util.Map;
import java.util.UUID;

/** Admin tools for agents. New agents can also be created with POST /api/agents or POST /api/admin/users. */
@RestController
@RequestMapping("/api/admin/agents")
@PreAuthorize("hasRole('ADMIN')")
public class AgentAdminController {
    private final UserRepository users;
    private final AgentProfileRepository profiles;
    private final AgentCommissionRepository commissions;
    private final Clock clock;

    public AgentAdminController(UserRepository users, AgentProfileRepository profiles,
            AgentCommissionRepository commissions, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.commissions = commissions;
        this.clock = clock;
    }

    @PostMapping("/{userId}/promote")
    @Transactional
    public AgentSummaryDto promote(@PathVariable UUID userId, @Valid @RequestBody(required = false) PromoteInput input) {
        User user = users.findForUpdate(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getRole() == Role.ADMIN) throw new BadRequestException("Administrators cannot be promoted to agents");
        if (user.getRole() != Role.AGENT) {
            user.setRole(Role.AGENT);
            user.setCredentialVersion(user.getCredentialVersion() + 1);
        }
        AgentProfile profile = profiles.findById(userId).orElseGet(() -> profiles.save(AgentProfile.builder().user(user)
                .active(true).referralCode(CodeGenerator.referralCode(user.getFirstName(), Year.now(clock).getValue()))
                .build()));
        profile.setActive(true);
        if (input != null && input.commissionRate() != null) profile.setCommissionRate(input.commissionRate());
        return new AgentSummaryDto(user.getId(), (user.getFirstName() + " " + user.getLastName()).trim(),
                user.getEmail(), profile.getReferralCode(), profile.getCommissionRate(), profile.isActive());
    }

    @PostMapping("/commissions/{id}/pay")
    @Transactional
    public Map<String, Object> markPaid(@PathVariable UUID id) {
        AgentCommission c = commissions.findById(id).orElseThrow(() -> new NotFoundException("Commission not found"));
        if (c.getStatus() != CommissionStatus.PENDING) throw new BadRequestException("Only pending commissions can be paid");
        c.setStatus(CommissionStatus.PAID);
        c.setPaidAt(clock.instant());
        return Map.of("id", c.getId(), "status", c.getStatus(), "paidAt", c.getPaidAt());
    }
}
