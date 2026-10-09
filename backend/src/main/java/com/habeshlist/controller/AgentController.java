package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.AgentProfile;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.AgentProfileRepository;
import com.habeshlist.repository.ShipmentRepository;
import com.habeshlist.security.AuthPrincipal;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.service.AuthService;
import com.habeshlist.service.ReferralService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AuthService authService;
    private final AgentProfileRepository agentProfiles;
    private final ReferralService referralService;
    private final ShipmentRepository shipments;

    public AgentController(AuthService authService, AgentProfileRepository agentProfiles,
                           ReferralService referralService, ShipmentRepository shipments) {
        this.authService = authService;
        this.agentProfiles = agentProfiles;
        this.referralService = referralService;
        this.shipments = shipments;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.UserDto> create(@Valid @RequestBody RequestDtos.CreateAgentRequest req) {
        User u = authService.createPrivilegedUser(req.firstName(), req.lastName(), req.email(),
                req.phone(), req.password(), Role.AGENT);
        AgentProfile ap = agentProfiles.findById(u.getId()).orElseThrow();
        ap.setCountry(req.country()); ap.setCity(req.city());
        if (req.commissionRate() != null) ap.setCommissionRate(req.commissionRate());
        agentProfiles.save(ap);
        return ResponseEntity.ok(ApiDtos.UserDto.from(u));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Object> list() {
        return agentProfiles.findAll().stream().map(ap -> (Object) java.util.Map.of(
                "user", ApiDtos.UserDto.from(ap.getUser()),
                "country", ap.getCountry(),
                "city", ap.getCity(),
                "referralCode", ap.getReferralCode(),
                "commissionRate", ap.getCommissionRate(),
                "active", ap.isActive()
        )).toList();
    }

    @GetMapping("/{id}/referrals")
    public ApiDtos.ReferralStatsDto stats(@PathVariable UUID id) {
        AuthPrincipal p = SecurityUtils.currentUser();
        if (p.role() != Role.ADMIN && !p.userId().equals(id)) throw new AccessDeniedException("Not allowed");
        return referralService.statsForAgent(id);
    }

    @GetMapping("/me/referrals")
    public ApiDtos.ReferralStatsDto myStats() {
        AuthPrincipal p = SecurityUtils.currentUser();
        if (p.role() != Role.AGENT) throw new AccessDeniedException("Not an agent");
        return referralService.statsForAgent(p.userId());
    }

    @GetMapping("/me/referred-shipments")
    public List<ApiDtos.ShipmentDto> myReferredShipments() {
        AuthPrincipal p = SecurityUtils.currentUser();
        if (p.role() != Role.AGENT) throw new AccessDeniedException("Not an agent");
        return shipments.findAllByReferringAgentId(p.userId()).stream()
                .map(ApiDtos.ShipmentDto::from).toList();
    }
}
