package com.habeshlist.service;

import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.*;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class LuggageService {

    private final LuggageRepository luggage;
    private final TripRepository trips;
    private final ShipmentRepository shipments;
    private final TrackingEventService trackingEventService;
    private final TripService tripService;

    public LuggageService(LuggageRepository luggage, TripRepository trips, ShipmentRepository shipments,
                          TrackingEventService trackingEventService, TripService tripService) {
        this.luggage = luggage;
        this.trips = trips;
        this.shipments = shipments;
        this.trackingEventService = trackingEventService;
        this.tripService = tripService;
    }

    @Transactional
    public Luggage createLuggage(RequestDtos.CreateLuggageRequest req) {
        Trip trip = trips.findById(req.tripId()).orElseThrow(() -> new NotFoundException("Trip not found"));
        Luggage l = Luggage.builder()
                .luggageNumber(tripService.nextLuggageNumber())
                .trip(trip).luggageType(req.luggageType())
                .capacityKg(req.capacityKg())
                .actualWeightKg(BigDecimal.ZERO)
                .status(LuggageStatus.CREATED)
                .sealNumber(req.sealNumber()).notes(req.notes())
                .build();
        return luggage.save(l);
    }

    @Transactional
    public Luggage assignAgent(UUID luggageId, User agent) {
        Luggage l = getById(luggageId);
        l.setAssignedAgent(agent);
        return luggage.save(l);
    }

    @Transactional
    public Luggage assignTraveler(UUID luggageId, User traveler) {
        Luggage l = getById(luggageId);
        l.setAssignedTraveler(traveler);
        return luggage.save(l);
    }

    @Transactional(readOnly = true)
    public Luggage getById(UUID id) {
        return luggage.findById(id).orElseThrow(() -> new NotFoundException("Luggage not found"));
    }

    public BigDecimal assignedWeight(UUID luggageId) {
        return shipments.findAllByLuggageId(luggageId).stream()
                .map(s -> s.getDeclaredWeightKg() == null ? BigDecimal.ZERO : s.getDeclaredWeightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void assignShipment(UUID luggageId, UUID shipmentId, User performedBy) {
        Luggage l = getById(luggageId);
        Shipment s = shipments.findById(shipmentId).orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (s.getLuggage() != null && !s.getLuggage().getId().equals(luggageId)) {
            throw new BadRequestException("Shipment already assigned to another luggage: " + s.getLuggage().getLuggageNumber());
        }
        BigDecimal already = assignedWeight(luggageId);
        BigDecimal weight = s.getDeclaredWeightKg() == null ? BigDecimal.ZERO : s.getDeclaredWeightKg();
        if (already.add(weight).compareTo(l.getCapacityKg()) > 0) {
            throw new BadRequestException("Capacity exceeded. Available: " +
                    l.getCapacityKg().subtract(already) + " kg");
        }
        s.setLuggage(l);
        s.setStatus(ShipmentStatus.ASSIGNED_TO_LUGGAGE);
        shipments.save(s);
        trackingEventService.record(s, ShipmentStatus.ASSIGNED_TO_LUGGAGE, "ASSIGNED_TO_LUGGAGE",
                "Package assigned to luggage", "Alexandria, VA", performedBy);
    }

    @Transactional
    public void unassignShipment(UUID luggageId, UUID shipmentId, User performedBy) {
        Shipment s = shipments.findById(shipmentId).orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (s.getLuggage() == null || !s.getLuggage().getId().equals(luggageId)) {
            throw new BadRequestException("Shipment not assigned to this luggage");
        }
        s.setLuggage(null);
        s.setStatus(ShipmentStatus.READY_FOR_CONSOLIDATION);
        shipments.save(s);
        trackingEventService.record(s, ShipmentStatus.READY_FOR_CONSOLIDATION, "UNASSIGNED_FROM_LUGGAGE",
                "Removed from luggage", "Alexandria, VA", performedBy);
    }

    /**
     * Simple greedy auto-assign: largest shipment first, placed in luggage with most remaining capacity.
     */
    @Transactional
    public int autoAssign(UUID tripId, User performedBy) {
        Trip trip = trips.findById(tripId).orElseThrow(() -> new NotFoundException("Trip not found"));
        List<Luggage> luggageList = luggage.findAllByTripId(tripId);
        List<Shipment> available = shipments.findAllByLuggageIsNullAndStatusIn(
                List.of(ShipmentStatus.RECEIVED_AT_ALEXANDRIA, ShipmentStatus.READY_FOR_CONSOLIDATION,
                        ShipmentStatus.PICKED_UP, ShipmentStatus.READY_FOR_PICKUP, ShipmentStatus.CREATED));

        // Sort shipments by weight desc
        available.sort((a, b) -> b.getDeclaredWeightKg().compareTo(a.getDeclaredWeightKg()));

        Map<UUID, BigDecimal> remaining = new HashMap<>();
        for (Luggage l : luggageList) remaining.put(l.getId(), l.getCapacityKg().subtract(assignedWeight(l.getId())));

        int assigned = 0;
        for (Shipment s : available) {
            // find luggage with most remaining capacity that still fits
            Luggage best = null; BigDecimal bestRem = BigDecimal.ZERO;
            for (Luggage l : luggageList) {
                BigDecimal rem = remaining.get(l.getId());
                if (rem.compareTo(s.getDeclaredWeightKg()) >= 0 && rem.compareTo(bestRem) > 0) {
                    best = l; bestRem = rem;
                }
            }
            if (best != null) {
                s.setLuggage(best);
                s.setStatus(ShipmentStatus.ASSIGNED_TO_LUGGAGE);
                shipments.save(s);
                remaining.put(best.getId(), bestRem.subtract(s.getDeclaredWeightKg()));
                trackingEventService.record(s, ShipmentStatus.ASSIGNED_TO_LUGGAGE, "ASSIGNED_TO_LUGGAGE",
                        "Package assigned to luggage", "Alexandria, VA", performedBy);
                assigned++;
            }
        }
        return assigned;
    }

    @Transactional
    public Luggage updateStatus(UUID luggageId, LuggageStatus status, User performedBy) {
        Luggage l = getById(luggageId);
        l.setStatus(status);
        luggage.save(l);
        ShipmentStatus mapped = switch (status) {
            case HANDED_TO_TRAVELER -> ShipmentStatus.HANDED_TO_TRAVELER;
            case IN_TRANSIT -> ShipmentStatus.DEPARTED_US;
            case ARRIVED_ETHIOPIA -> ShipmentStatus.ARRIVED_ETHIOPIA;
            case RECEIVED_BY_AGENT -> ShipmentStatus.RECEIVED_BY_AGENT;
            default -> null;
        };
        if (mapped != null) {
            for (Shipment s : shipments.findAllByLuggageId(l.getId())) {
                s.setStatus(mapped);
                shipments.save(s);
                trackingEventService.record(s, mapped, "LUGGAGE_" + status.name(),
                        "Luggage " + status.name(), null, performedBy);
            }
        }
        return l;
    }
}
