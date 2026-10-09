package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.Luggage;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.LuggageStatus;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.security.AuthPrincipal;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.service.DtoMapper;
import com.habeshlist.service.LuggageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/luggage")
public class LuggageController {

    private final LuggageService luggageService;
    private final UserRepository users;
    private final DtoMapper mapper;

    public LuggageController(LuggageService luggageService, UserRepository users, DtoMapper mapper) {
        this.luggageService = luggageService;
        this.users = users;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.LuggageDto> create(@Valid @RequestBody RequestDtos.CreateLuggageRequest req) {
        Luggage l = luggageService.createLuggage(req);
        return ResponseEntity.ok(mapper.toLuggageDto(l));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiDtos.LuggageDto> get(@PathVariable UUID id) {
        AuthPrincipal p = SecurityUtils.currentUser();
        Luggage l = luggageService.getById(id);
        // sender cannot read luggage
        if (p.role() == Role.SENDER) throw new AccessDeniedException("Not allowed");
        if (p.role() == Role.TRAVELER && !isTravelerOf(l, p.userId()))
            throw new AccessDeniedException("Not your luggage");
        return ResponseEntity.ok(mapper.toLuggageDto(l));
    }

    private static boolean isTravelerOf(Luggage l, UUID userId) {
        return l.getTrip().getTraveler().getId().equals(userId)
                || (l.getAssignedTraveler() != null && l.getAssignedTraveler().getId().equals(userId));
    }

    @PutMapping("/{id}/agent")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.LuggageDto> assignAgent(@PathVariable UUID id,
                                                          @RequestBody RequestDtos.AssignAgentRequest req) {
        User agent = null;
        if (req.agentId() != null) {
            agent = users.findById(req.agentId()).orElseThrow(() -> new NotFoundException("Agent not found"));
            if (agent.getRole() != Role.AGENT) throw new BadRequestException("Selected user is not an agent");
        }
        return ResponseEntity.ok(mapper.toLuggageDto(luggageService.assignAgent(id, agent)));
    }

    @PutMapping("/{id}/traveler")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.LuggageDto> assignTraveler(@PathVariable UUID id,
                                                             @RequestBody RequestDtos.AssignTravelerRequest req) {
        User traveler = null;
        if (req.travelerId() != null) {
            traveler = users.findById(req.travelerId()).orElseThrow(() -> new NotFoundException("Traveler not found"));
            if (traveler.getRole() != Role.TRAVELER) throw new BadRequestException("Selected user is not a traveler");
        }
        return ResponseEntity.ok(mapper.toLuggageDto(luggageService.assignTraveler(id, traveler)));
    }

    @PostMapping("/{id}/shipments")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiDtos.LuggageDto> assign(@PathVariable UUID id,
                                                     @Valid @RequestBody RequestDtos.AssignShipmentRequest req) {
        AuthPrincipal p = SecurityUtils.currentUser();
        User performed = users.findById(p.userId()).orElseThrow();
        luggageService.assignShipment(id, req.shipmentId(), performed);
        return ResponseEntity.ok(mapper.toLuggageDto(luggageService.getById(id)));
    }

    @DeleteMapping("/{id}/shipments/{shipmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiDtos.LuggageDto> unassign(@PathVariable UUID id, @PathVariable UUID shipmentId) {
        AuthPrincipal p = SecurityUtils.currentUser();
        User performed = users.findById(p.userId()).orElseThrow();
        luggageService.unassignShipment(id, shipmentId, performed);
        return ResponseEntity.ok(mapper.toLuggageDto(luggageService.getById(id)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiDtos.LuggageDto> updateStatus(@PathVariable UUID id,
                                                           @Valid @RequestBody RequestDtos.UpdateLuggageStatusRequest req) {
        AuthPrincipal p = SecurityUtils.currentUser();
        Luggage l = luggageService.getById(id);
        if (p.role() == Role.SENDER) throw new AccessDeniedException("Not allowed");
        if (p.role() == Role.TRAVELER && !isTravelerOf(l, p.userId()))
            throw new AccessDeniedException("Not your luggage");
        LuggageStatus st;
        try { st = LuggageStatus.valueOf(req.status()); }
        catch (IllegalArgumentException e) { throw new BadRequestException("Invalid status"); }
        User performed = users.findById(p.userId()).orElseThrow();
        Luggage updated = luggageService.updateStatus(id, st, performed);
        return ResponseEntity.ok(mapper.toLuggageDto(updated));
    }

    @PostMapping("/trips/{tripId}/auto-assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> autoAssign(@PathVariable UUID tripId) {
        AuthPrincipal p = SecurityUtils.currentUser();
        User performed = users.findById(p.userId()).orElseThrow();
        int assigned = luggageService.autoAssign(tripId, performed);
        return ResponseEntity.ok(Map.of("assigned", assigned));
    }
}
