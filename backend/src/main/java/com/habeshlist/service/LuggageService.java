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
        Trip trip = req.tripId() == null ? null : tripService.lockForAssignment(req.tripId());
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

    @Transactional(readOnly = true)
    public List<Luggage> list() {
        return luggage.findAll();
    }

    @Transactional
    public Luggage assignTrip(UUID luggageId, UUID tripId, User performedBy) {
        Luggage existing = getById(luggageId);
        UUID lockId = existing.getTrip() == null ? tripId : existing.getTrip().getId();
        Trip trip = lockId == null ? null : tripService.lockForAssignment(lockId);
        Luggage l = luggage.findForUpdate(luggageId)
                .orElseThrow(() -> new NotFoundException("Luggage not found"));
        assertNotDispatched(l);
        if (l.getTrip() != null && tripId != null) {
            if (l.getTrip().getId().equals(tripId)) return l;
            throw new BadRequestException("Remove luggage from its current trip before reassigning");
        }
        if (tripId != null) {
            if (trip == null || !trip.getId().equals(tripId)) {
                throw new BadRequestException("Luggage assignment changed; refresh and try again");
            }
            tripService.checkCapacity(trip, assignedWeight(luggageId));
        }
        if (l.getTrip() == null && tripId == null) return l;
        if (tripId == null) {
            for (Shipment s : shipments.findAllByLuggageId(luggageId)) {
                trackingEventService.record(s, s.getStatus(), "LUGGAGE_REMOVED_FROM_TRIP",
                        "Luggage removed from trip " + l.getTrip().getTripNumber(),
                        "Alexandria, VA", performedBy);
            }
        }
        l.setTrip(tripId == null ? null : trip);
        luggage.save(l);
        if (tripId != null) {
            for (Shipment s : shipments.findAllByLuggageId(luggageId)) {
                trackingEventService.record(s, s.getStatus(), "LUGGAGE_ASSIGNED_TO_TRIP",
                        "Luggage assigned to trip " + trip.getTripNumber(),
                        "Alexandria, VA", performedBy);
            }
        }
        return l;
    }

    private void assertNotDispatched(Luggage l) {
        if (l.getStatus() != LuggageStatus.CREATED && l.getStatus() != LuggageStatus.PACKING
                && l.getStatus() != LuggageStatus.READY && l.getStatus() != LuggageStatus.SEALED) {
            throw new BadRequestException("Luggage has already been dispatched or closed");
        }
    }

    private Luggage lockForPacking(UUID id) {
        Luggage existing = getById(id);
        if (existing.getTrip() != null) tripService.lockForAssignment(existing.getTrip().getId());
        Luggage l = luggage.findForUpdate(id).orElseThrow(() -> new NotFoundException("Luggage not found"));
        assertNotDispatched(l);
        if (l.getStatus() == LuggageStatus.SEALED) {
            throw new BadRequestException("Reopen the luggage for packing before changing its contents");
        }
        return l;
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
    public void assignShipments(UUID luggageId, List<UUID> shipmentIds, User performedBy) {
        Luggage l = lockForPacking(luggageId);
        List<Shipment> selected = shipmentIds.stream().distinct().sorted()
                .map(id -> shipments.findForUpdate(id).orElseThrow(() -> new NotFoundException("Shipment not found")))
                .toList();
        BigDecimal additionalWeight = BigDecimal.ZERO;
        for (Shipment s : selected) {
            if (s.getLuggage() != null && s.getLuggage().getId().equals(luggageId)) continue;
            assertEligibleForPacking(s);
            additionalWeight = additionalWeight.add(TripService.assignmentWeight(s));
        }
        if (assignedWeight(luggageId).add(additionalWeight).compareTo(l.getCapacityKg()) > 0) {
            throw new BadRequestException("Selected packages exceed luggage capacity");
        }
        if (l.getTrip() != null) tripService.checkCapacity(l.getTrip(), additionalWeight);
        for (Shipment s : selected) {
            assignShipment(luggageId, s.getId(), performedBy);
        }
    }

    private void assertEligibleForPacking(Shipment s) {
        if (s.getLuggage() != null) {
            throw new BadRequestException("Shipment already assigned to another luggage: " + s.getLuggage().getLuggageNumber());
        }
        if (s.getTrip() != null) {
            throw new BadRequestException("Remove the package from its direct trip before consolidating it");
        }
        if (!s.getStatus().canAssignToTransport()) {
            throw new BadRequestException("Package status is not eligible for consolidation");
        }
    }

    @Transactional
    public void assignShipment(UUID luggageId, UUID shipmentId, User performedBy) {
        Luggage l = lockForPacking(luggageId);
        Shipment s = shipments.findForUpdate(shipmentId).orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (s.getLuggage() != null && s.getLuggage().getId().equals(luggageId)) return;
        assertEligibleForPacking(s);
        BigDecimal already = assignedWeight(luggageId);
        BigDecimal weight = TripService.assignmentWeight(s);
        if (already.add(weight).compareTo(l.getCapacityKg()) > 0) {
            throw new BadRequestException("Capacity exceeded. Available: " +
                    l.getCapacityKg().subtract(already) + " kg");
        }
        if (l.getTrip() != null) tripService.checkCapacity(l.getTrip(), weight);
        l.setStatus(LuggageStatus.PACKING);
        luggage.save(l);
        s.setLuggage(l);
        s.setStatus(ShipmentStatus.ASSIGNED_TO_LUGGAGE);
        shipments.save(s);
        trackingEventService.record(s, ShipmentStatus.ASSIGNED_TO_LUGGAGE, "ASSIGNED_TO_LUGGAGE",
                "Package assigned to luggage", "Alexandria, VA", performedBy);
    }

    @Transactional
    public void unassignShipment(UUID luggageId, UUID shipmentId, User performedBy) {
        Luggage l = lockForPacking(luggageId);
        Shipment s = shipments.findForUpdate(shipmentId).orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (s.getLuggage() == null || !s.getLuggage().getId().equals(luggageId)) {
            throw new BadRequestException("Shipment not assigned to this luggage");
        }
        l.setStatus(LuggageStatus.PACKING);
        luggage.save(l);
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
        Trip trip = tripService.lockForAssignment(tripId);
        List<Luggage> luggageList = luggage.findAllByTripId(tripId).stream()
                .filter(l -> l.getStatus() == LuggageStatus.CREATED || l.getStatus() == LuggageStatus.PACKING
                        || l.getStatus() == LuggageStatus.READY).toList();
        List<Shipment> available = shipments.findAllByLuggageIsNullAndStatusIn(
                List.of(ShipmentStatus.RECEIVED_AT_ALEXANDRIA, ShipmentStatus.READY_FOR_CONSOLIDATION,
                        ShipmentStatus.PICKED_UP, ShipmentStatus.READY_FOR_PICKUP, ShipmentStatus.CREATED))
                .stream().filter(s -> s.getTrip() == null)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // Sort shipments by weight desc
        available.sort((a, b) -> TripService.assignmentWeight(b).compareTo(TripService.assignmentWeight(a)));

        Map<UUID, BigDecimal> remaining = new HashMap<>();
        for (Luggage l : luggageList) remaining.put(l.getId(), l.getCapacityKg().subtract(assignedWeight(l.getId())));
        BigDecimal tripRemaining = trip.getTotalLuggageCapacity().subtract(
                shipments.findAllOnTrip(tripId).stream()
                        .map(TripService::assignmentWeight).reduce(BigDecimal.ZERO, BigDecimal::add));

        int assigned = 0;
        for (Shipment s : available) {
            if (s.getDeclaredWeightKg().compareTo(tripRemaining) > 0) continue;
            // find luggage with most remaining capacity that still fits
            Luggage best = null; BigDecimal bestRem = BigDecimal.ZERO;
            for (Luggage l : luggageList) {
                BigDecimal rem = remaining.get(l.getId());
                if (rem.compareTo(s.getDeclaredWeightKg()) >= 0 && rem.compareTo(bestRem) > 0) {
                    best = l; bestRem = rem;
                }
            }
            if (best != null) {
                assignShipment(best.getId(), s.getId(), performedBy);
                remaining.put(best.getId(), bestRem.subtract(s.getDeclaredWeightKg()));
                tripRemaining = tripRemaining.subtract(s.getDeclaredWeightKg());
                assigned++;
            }
        }
        return assigned;
    }

    @Transactional
    public Luggage updateStatus(UUID luggageId, LuggageStatus status, User performedBy) {
        Luggage existing = getById(luggageId);
        if (existing.getTrip() != null) {
            trips.findForUpdate(existing.getTrip().getId()).orElseThrow(() -> new NotFoundException("Trip not found"));
        }
        Luggage l = luggage.findForUpdate(luggageId).orElseThrow(() -> new NotFoundException("Luggage not found"));
        if (status == LuggageStatus.CREATED || status == LuggageStatus.PACKING
                || status == LuggageStatus.READY || status == LuggageStatus.SEALED) {
            assertNotDispatched(l);
            if (l.getTrip() != null) tripService.assertCanAssign(l.getTrip());
        }
        if (l.getTrip() == null && status != LuggageStatus.CREATED && status != LuggageStatus.PACKING
                && status != LuggageStatus.READY && status != LuggageStatus.SEALED) {
            throw new BadRequestException("Assign luggage to a trip before dispatching it");
        }
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
