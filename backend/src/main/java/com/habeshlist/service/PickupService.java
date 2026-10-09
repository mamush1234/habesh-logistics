package com.habeshlist.service;

import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.PickupStatus;
import com.habeshlist.entity.enums.ShipmentStatus;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.PickupRepository;
import com.habeshlist.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PickupService {

    private final PickupRepository pickups;
    private final ShipmentRepository shipments;
    private final TrackingEventService trackingEventService;

    public PickupService(PickupRepository pickups, ShipmentRepository shipments,
                         TrackingEventService trackingEventService) {
        this.pickups = pickups;
        this.shipments = shipments;
        this.trackingEventService = trackingEventService;
    }

    @Transactional
    public Pickup create(RequestDtos.CreatePickupRequest req) {
        Shipment s = shipments.findById(req.shipmentId())
                .orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (pickups.findByShipmentId(s.getId()).isPresent()) {
            throw new BadRequestException("Pickup already exists for this shipment");
        }
        Pickup p = Pickup.builder()
                .shipment(s).address(req.address()).date(req.date())
                .timeWindow(req.timeWindow()).notes(req.notes())
                .status(PickupStatus.REQUESTED).build();
        return pickups.save(p);
    }

    @Transactional(readOnly = true)
    public List<Pickup> listAll() { return pickups.findAll(); }

    @Transactional
    public Pickup updateStatus(UUID pickupId, PickupStatus status, User performedBy) {
        Pickup p = pickups.findById(pickupId).orElseThrow(() -> new NotFoundException("Pickup not found"));
        p.setStatus(status);
        Shipment s = p.getShipment();
        switch (status) {
            case SCHEDULED, ASSIGNED, EN_ROUTE -> {
                s.setStatus(ShipmentStatus.PICKUP_SCHEDULED);
                trackingEventService.record(s, ShipmentStatus.PICKUP_SCHEDULED, "PICKUP_" + status.name(),
                        "Pickup " + status.name(), p.getAddress(), performedBy);
            }
            case PICKED_UP -> {
                s.setStatus(ShipmentStatus.PICKED_UP);
                p.setCompletedAt(Instant.now());
                trackingEventService.record(s, ShipmentStatus.PICKED_UP, "PICKED_UP",
                        "Package picked up", p.getAddress(), performedBy);
            }
            case FAILED, CANCELLED -> trackingEventService.record(s, s.getStatus(), "PICKUP_" + status.name(),
                    "Pickup " + status.name(), p.getAddress(), performedBy);
            default -> {}
        }
        shipments.save(s);
        return pickups.save(p);
    }
}
