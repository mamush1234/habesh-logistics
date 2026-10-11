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
import com.habeshlist.security.ShipmentAccess;
import com.habeshlist.service.DtoMapper;
import com.habeshlist.service.LuggageService;
import com.habeshlist.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/luggage")
public class LuggageController {

    private final LuggageService luggageService;
    private final UserRepository users;
    private final DtoMapper mapper;
    private final ShipmentAccess access;
    private final ShipmentService shipments;

    public LuggageController(LuggageService luggageService, UserRepository users, DtoMapper mapper,
                             ShipmentAccess access, ShipmentService shipments) {
        this.luggageService = luggageService;
        this.users = users;
        this.mapper = mapper;
        this.access = access;
        this.shipments = shipments;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.LuggageDto> create(@Valid @RequestBody RequestDtos.CreateLuggageRequest req) {
        Luggage l = luggageService.createLuggage(req);
        return ResponseEntity.ok(mapper.toLuggageDto(l));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiDtos.LuggageDto> list() {
        return luggageService.list().stream().map(mapper::toLuggageDto).toList();
    }

    @PutMapping("/{id}/trip")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.LuggageDto> assignTrip(@PathVariable UUID id,
                                                        @RequestBody RequestDtos.AssignTripRequest req) {
        User performed = users.findById(SecurityUtils.currentUser().userId()).orElseThrow();
        return ResponseEntity.ok(mapper.toLuggageDto(luggageService.assignTrip(id, req.tripId(), performed)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiDtos.LuggageDto> get(@PathVariable UUID id) {
        AuthPrincipal p = SecurityUtils.currentUser();
        Luggage l = luggageService.getById(id);
        access.assertCanRead(p, l);
        return ResponseEntity.ok(mapper.toLuggageDto(l));
    }

    private void assertCanManage(AuthPrincipal user, UUID id) {
        if (!ShipmentAccess.isAssigned(user, luggageService.getById(id))) {
            throw new AccessDeniedException("Only assigned staff can change this luggage");
        }
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
        assertCanManage(p, id);
        ShipmentAccess.assertCanRead(p, shipments.getById(req.shipmentId()));
        User performed = users.findById(p.userId()).orElseThrow();
        luggageService.assignShipment(id, req.shipmentId(), performed);
        return ResponseEntity.ok(mapper.toLuggageDto(luggageService.getById(id)));
    }

    @DeleteMapping("/{id}/shipments/{shipmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiDtos.LuggageDto> unassign(@PathVariable UUID id, @PathVariable UUID shipmentId) {
        AuthPrincipal p = SecurityUtils.currentUser();
        assertCanManage(p, id);
        ShipmentAccess.assertCanRead(p, shipments.getById(shipmentId));
        User performed = users.findById(p.userId()).orElseThrow();
        luggageService.unassignShipment(id, shipmentId, performed);
        return ResponseEntity.ok(mapper.toLuggageDto(luggageService.getById(id)));
    }

    @PostMapping("/{id}/shipments/batch")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiDtos.LuggageDto> assignBatch(@PathVariable UUID id,
                                                          @Valid @RequestBody RequestDtos.AssignShipmentsRequest req) {
        AuthPrincipal p = SecurityUtils.currentUser();
        assertCanManage(p, id);
        for (UUID shipmentId : req.shipmentIds()) {
            ShipmentAccess.assertCanRead(p, shipments.getById(shipmentId));
        }
        User performed = users.findById(p.userId()).orElseThrow();
        luggageService.assignShipments(id, req.shipmentIds(), performed);
        return ResponseEntity.ok(mapper.toLuggageDto(luggageService.getById(id)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiDtos.LuggageDto> updateStatus(@PathVariable UUID id,
                                                           @Valid @RequestBody RequestDtos.UpdateLuggageStatusRequest req) {
        AuthPrincipal p = SecurityUtils.currentUser();
        assertCanManage(p, id);
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
