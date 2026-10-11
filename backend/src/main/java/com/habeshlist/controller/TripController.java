package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.Trip;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.entity.enums.TripStatus;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.security.AuthPrincipal;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.security.ShipmentAccess;
import com.habeshlist.service.DtoMapper;
import com.habeshlist.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;
    private final DtoMapper mapper;
    private final UserRepository users;
    private final ShipmentAccess access;

    public TripController(TripService tripService, DtoMapper mapper, UserRepository users,
                          ShipmentAccess access) {
        this.tripService = tripService;
        this.mapper = mapper;
        this.users = users;
        this.access = access;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.TripDto> create(@Valid @RequestBody RequestDtos.CreateTripRequest req) {
        Trip t = tripService.createTrip(req);
        return ResponseEntity.ok(mapper.toTripDto(t));
    }

    @GetMapping
    public List<ApiDtos.TripDto> list() {
        AuthPrincipal p = SecurityUtils.currentUser();
        return tripService.listForUser(p).stream().map(mapper::toTripDto).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiDtos.TripDto> get(@PathVariable UUID id) {
        AuthPrincipal p = SecurityUtils.currentUser();
        Trip t = tripService.getById(id);
        access.assertCanRead(p, t);
        return ResponseEntity.ok(mapper.toTripDto(t));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiDtos.TripDto> updateStatus(@PathVariable UUID id,
                                                        @Valid @RequestBody RequestDtos.UpdateTripStatusRequest req) {
        AuthPrincipal p = SecurityUtils.currentUser();
        Trip t = tripService.getById(id);
        if (p.role() != Role.ADMIN && !(p.role() == Role.TRAVELER && t.getTraveler().getId().equals(p.userId()))) {
            throw new AccessDeniedException("Not allowed");
        }
        TripStatus st;
        try { st = TripStatus.valueOf(req.status()); }
        catch (IllegalArgumentException e) { throw new BadRequestException("Invalid status"); }
        User performed = users.findById(p.userId()).orElseThrow();
        t = tripService.updateStatus(id, st, performed);
        return ResponseEntity.ok(mapper.toTripDto(t));
    }

}
