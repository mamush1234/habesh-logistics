package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.Shipment;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.entity.enums.ShipmentStatus;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.TrackingEventRepository;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.security.AuthPrincipal;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.security.ShipmentAccess;
import com.habeshlist.service.DtoMapper;
import com.habeshlist.service.ShipmentService;
import com.habeshlist.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final UserRepository users;
    private final TrackingEventRepository events;
    private final DtoMapper mapper;
    private final TripService tripService;

    public ShipmentController(ShipmentService shipmentService, UserRepository users,
                              TrackingEventRepository events, DtoMapper mapper, TripService tripService) {
        this.shipmentService = shipmentService;
        this.users = users;
        this.events = events;
        this.mapper = mapper;
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<ApiDtos.ShipmentDto> create(@Valid @RequestBody RequestDtos.CreateShipmentRequest req) {
        AuthPrincipal p = SecurityUtils.currentUser();
        Shipment s = shipmentService.createShipment(p, req);
        return ResponseEntity.ok(ApiDtos.ShipmentDto.from(s));
    }

    @GetMapping
    public List<ApiDtos.ShipmentDto> list() {
        AuthPrincipal p = SecurityUtils.currentUser();
        return shipmentService.listForUser(p).stream().map(ApiDtos.ShipmentDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiDtos.ShipmentDto> get(@PathVariable UUID id) {
        AuthPrincipal p = SecurityUtils.currentUser();
        Shipment s = shipmentService.getById(id);
        assertCanRead(p, s);
        return ResponseEntity.ok(ApiDtos.ShipmentDto.from(s));
    }

    /** Tracking by tracking number; login required and limited to people linked to the shipment. */
    @GetMapping("/tracking/{trackingNumber}")
    public ResponseEntity<ApiDtos.PublicTrackingDto> publicTrack(@PathVariable String trackingNumber) {
        AuthPrincipal p = SecurityUtils.currentUser();
        Shipment s = shipmentService.getByTrackingNumber(trackingNumber);
        assertCanRead(p, s);
        return ResponseEntity.ok(mapper.toPublicTrackingDto(s));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<java.util.Map<String, String>> photo(@PathVariable UUID id) {
        AuthPrincipal p = SecurityUtils.currentUser();
        Shipment s = shipmentService.getById(id);
        assertCanRead(p, s);
        if (s.getPhotoData() == null) throw new NotFoundException("No photo");
        return ResponseEntity.ok(java.util.Map.of("photo", s.getPhotoData()));
    }

    @GetMapping("/{id}/tracking")
    public List<ApiDtos.TrackingEventDto> tracking(@PathVariable UUID id) {
        AuthPrincipal p = SecurityUtils.currentUser();
        Shipment s = shipmentService.getById(id);
        assertCanRead(p, s);
        return events.findAllByShipmentIdOrderByCreatedAtAsc(id).stream()
                .map(ApiDtos.TrackingEventDto::from).toList();
    }

    @GetMapping("/{id}/photos")
    public ResponseEntity<java.util.Map<String, List<String>>> photos(@PathVariable UUID id) {
        Shipment s = shipmentService.getById(id);
        assertCanRead(SecurityUtils.currentUser(), s);
        return ResponseEntity.ok(java.util.Map.of("photos", s.allPhotos()));
    }

    @PutMapping("/{id}/photos")
    @PreAuthorize("hasAnyRole('ADMIN','SENDER')")
    public ResponseEntity<ApiDtos.ShipmentDto> updatePhotos(@PathVariable UUID id,
            @Valid @RequestBody RequestDtos.UpdatePhotosRequest req) {
        return ResponseEntity.ok(ApiDtos.ShipmentDto.from(
                shipmentService.updatePhotos(id, req.photoDataUrls(), SecurityUtils.currentUser())));
    }

    @PutMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('ADMIN','SENDER')")
    public ResponseEntity<ApiDtos.ShipmentDto> updatePhoto(@PathVariable UUID id,
                                                          @Valid @RequestBody RequestDtos.UpdatePhotoRequest req) {
        return ResponseEntity.ok(ApiDtos.ShipmentDto.from(
                shipmentService.updatePhoto(id, req.photoDataUrl(), SecurityUtils.currentUser())));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiDtos.ShipmentDto> updateStatus(@PathVariable UUID id,
                                                            @Valid @RequestBody RequestDtos.UpdateStatusRequest req) {
        AuthPrincipal p = SecurityUtils.currentUser();
        if (p.role() == Role.SENDER) throw new AccessDeniedException("Not allowed");
        if (p.role() != Role.ADMIN) {
            Shipment target = shipmentService.getById(id);
            ShipmentAccess.assertCanRead(p, target);
            boolean assigned = (p.role() == Role.AGENT
                    && ((target.getAssignedAgent() != null && target.getAssignedAgent().getId().equals(p.userId()))
                        || (target.getLuggage() != null
                            && ShipmentAccess.isAssigned(p, target.getLuggage()))))
                    || (p.role() == Role.TRAVELER && target.isCarriedBy(p.userId()));
            if (!assigned) throw new AccessDeniedException("Only the assigned traveler/agent or an admin can change status");
        }
        User performed = users.findById(p.userId()).orElseThrow();
        ShipmentStatus newStatus;
        try { newStatus = ShipmentStatus.valueOf(req.status()); }
        catch (IllegalArgumentException e) { throw new com.habeshlist.exception.BadRequestException("Invalid status"); }
        Shipment s = shipmentService.updateStatus(id, newStatus, req.location(), req.description(), performed);
        return ResponseEntity.ok(ApiDtos.ShipmentDto.from(s));
    }

    @PutMapping("/{id}/payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.ShipmentDto> updatePayment(@PathVariable UUID id,
                                                             @Valid @RequestBody RequestDtos.UpdatePaymentRequest req) {
        return ResponseEntity.ok(ApiDtos.ShipmentDto.from(shipmentService.updatePayment(id, req.paymentStatus())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        shipmentService.remove(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/agent")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.ShipmentDto> assignAgent(@PathVariable UUID id,
                                                           @RequestBody RequestDtos.AssignAgentRequest req) {
        return ResponseEntity.ok(ApiDtos.ShipmentDto.from(shipmentService.assignAgent(id, req.agentId())));
    }

    @PutMapping("/{id}/traveler")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.ShipmentDto> assignTraveler(@PathVariable UUID id,
                                                              @RequestBody RequestDtos.AssignTravelerRequest req) {
        return ResponseEntity.ok(ApiDtos.ShipmentDto.from(shipmentService.assignTraveler(id, req.travelerId())));
    }

    @PutMapping("/{id}/trip")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiDtos.ShipmentDto> assignTrip(@PathVariable UUID id,
                                                          @RequestBody RequestDtos.AssignTripRequest req) {
        User performed = users.findById(SecurityUtils.currentUser().userId()).orElseThrow();
        return ResponseEntity.ok(ApiDtos.ShipmentDto.from(tripService.assignShipment(id, req.tripId(), performed)));
    }

    public static void assertCanRead(AuthPrincipal p, Shipment s) {
        ShipmentAccess.assertCanRead(p, s);
    }
}
