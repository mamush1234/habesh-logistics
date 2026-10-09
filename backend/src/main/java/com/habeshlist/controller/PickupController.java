package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.Pickup;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.PickupStatus;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.security.AuthPrincipal;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.service.DtoMapper;
import com.habeshlist.service.PickupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pickups")
public class PickupController {

    private final PickupService service;
    private final UserRepository users;
    private final DtoMapper mapper;

    public PickupController(PickupService service, UserRepository users, DtoMapper mapper) {
        this.service = service; this.users = users; this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ApiDtos.PickupDto> create(@Valid @RequestBody RequestDtos.CreatePickupRequest req) {
        Pickup p = service.create(req);
        return ResponseEntity.ok(mapper.toPickupDto(p));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiDtos.PickupDto> list() {
        return service.listAll().stream().map(mapper::toPickupDto).toList();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.PickupDto> status(@PathVariable UUID id,
                                                    @Valid @RequestBody RequestDtos.UpdatePickupStatusRequest req) {
        AuthPrincipal pr = SecurityUtils.currentUser();
        User performed = users.findById(pr.userId()).orElseThrow();
        PickupStatus st;
        try { st = PickupStatus.valueOf(req.status()); }
        catch (IllegalArgumentException e) { throw new BadRequestException("Invalid status"); }
        return ResponseEntity.ok(mapper.toPickupDto(service.updateStatus(id, st, performed)));
    }
}
