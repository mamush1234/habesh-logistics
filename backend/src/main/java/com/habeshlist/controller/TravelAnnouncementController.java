package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.service.TravelAnnouncementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/travel-announcements")
public class TravelAnnouncementController {
    private final TravelAnnouncementService service;

    public TravelAnnouncementController(TravelAnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApiDtos.TravelAnnouncementDto> list() {
        return service.list();
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public List<ApiDtos.TravelBookingRequestDto> requests() {
        return service.requests();
    }

    @PutMapping("/{id}/capacity")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ApiDtos.TravelAnnouncementDto updateCapacity(@PathVariable UUID id,
            @Valid @RequestBody RequestDtos.UpdateTravelCapacityRequest req) {
        return service.updateCapacity(id, req);
    }

    @PutMapping("/{id}/requests/{shipmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ApiDtos.TravelBookingRequestDto decide(@PathVariable UUID id, @PathVariable UUID shipmentId,
            @Valid @RequestBody RequestDtos.DecideTravelBookingRequest req) {
        return service.decide(id, shipmentId, req.status());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ApiDtos.TravelAnnouncementDto create(
            @Valid @RequestBody RequestDtos.CreateTravelAnnouncementRequest req) {
        return service.create(req);
    }
}
