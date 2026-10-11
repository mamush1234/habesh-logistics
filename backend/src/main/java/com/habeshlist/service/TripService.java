package com.habeshlist.service;

import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.*;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.*;
import com.habeshlist.security.AuthPrincipal;
import com.habeshlist.security.ShipmentAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TripService {

    private final TripRepository trips;
    private final UserRepository users;
    private final LuggageRepository luggage;
    private final ShipmentRepository shipments;
    private final TrackingEventService trackingEventService;
    private final ShipmentAccess access;

    private static final AtomicInteger TRIP_SEQ = new AtomicInteger(44);
    private static final AtomicInteger LUG_SEQ = new AtomicInteger(0);

    public TripService(TripRepository trips, UserRepository users, LuggageRepository luggage,
                       ShipmentRepository shipments, TrackingEventService trackingEventService,
                       ShipmentAccess access) {
        this.trips = trips;
        this.users = users;
        this.luggage = luggage;
        this.shipments = shipments;
        this.trackingEventService = trackingEventService;
        this.access = access;
    }

    public synchronized void initSequences() {
        TRIP_SEQ.set(44 + (int) trips.count());
        LUG_SEQ.set((int) luggage.count());
    }

    public String nextTripNumber() {
        return String.format("TRIP-%d-%04d", Year.now().getValue(), TRIP_SEQ.incrementAndGet());
    }
    public String nextLuggageNumber() {
        return String.format("LUG-%d-%04d", Year.now().getValue(), LUG_SEQ.incrementAndGet() + 1);
    }

    @Transactional
    public Trip createTrip(RequestDtos.CreateTripRequest req) {
        User traveler = users.findById(req.travelerId())
                .orElseThrow(() -> new NotFoundException("Traveler not found"));
        if (traveler.getRole() != Role.TRAVELER) throw new BadRequestException("User is not a traveler");

        Trip t = Trip.builder()
                .tripNumber(nextTripNumber())
                .traveler(traveler)
                .departureAirport(req.departureAirport().toUpperCase())
                .arrivalAirport(req.arrivalAirport().toUpperCase())
                .departureDate(req.departureDate())
                .expectedArrivalDate(req.expectedArrivalDate())
                .totalLuggageCapacity(req.totalLuggageCapacity())
                .numberOfLuggage(req.numberOfLuggage())
                .status(TripStatus.OPEN)
                .notes(req.notes())
                .build();
        trips.save(t);
        // Pre-create luggage units
        BigDecimal per = req.numberOfLuggage() == 0 ? BigDecimal.ZERO : req.totalLuggageCapacity()
                .divide(BigDecimal.valueOf(req.numberOfLuggage()), 2, java.math.RoundingMode.HALF_UP);
        for (int i = 0; i < req.numberOfLuggage(); i++) {
            Luggage l = Luggage.builder()
                    .luggageNumber(nextLuggageNumber())
                    .trip(t).capacityKg(per)
                    .actualWeightKg(BigDecimal.ZERO)
                    .status(LuggageStatus.CREATED)
                    .build();
            luggage.save(l);
        }
        return t;
    }

    @Transactional(readOnly = true)
    public Trip getById(UUID id) {
        return trips.findById(id).orElseThrow(() -> new NotFoundException("Trip not found"));
    }

    public Trip lockForAssignment(UUID id) {
        Trip t = trips.findForUpdate(id).orElseThrow(() -> new NotFoundException("Trip not found"));
        assertCanAssign(t);
        return t;
    }

    public void assertCanAssign(Trip t) {
        if (t.getStatus() != TripStatus.OPEN && t.getStatus() != TripStatus.FILLING
                && t.getStatus() != TripStatus.READY) {
            throw new BadRequestException("Trip is no longer open for assignment");
        }
    }

    public void checkCapacity(Trip t, BigDecimal additionalWeight) {
        BigDecimal assigned = shipments.findAllOnTrip(t.getId()).stream()
                .map(s -> s.getDeclaredWeightKg() == null ? BigDecimal.ZERO : s.getDeclaredWeightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (assigned.add(additionalWeight).compareTo(t.getTotalLuggageCapacity()) > 0) {
            throw new BadRequestException("Trip capacity exceeded. Available: "
                    + t.getTotalLuggageCapacity().subtract(assigned) + " kg");
        }
    }

    public static BigDecimal assignmentWeight(Shipment s) {
        if (s.getDeclaredWeightKg() == null || s.getDeclaredWeightKg().signum() <= 0) {
            throw new BadRequestException("Package must have a positive declared weight before assignment");
        }
        return s.getDeclaredWeightKg();
    }

    @Transactional
    public Shipment assignShipment(UUID shipmentId, UUID tripId, User performedBy) {
        Shipment existing = shipments.findById(shipmentId)
                .orElseThrow(() -> new NotFoundException("Shipment not found"));
        // Lock the trip before its packages so capacity checks are serialized.
        UUID lockId = existing.getTrip() == null ? tripId : existing.getTrip().getId();
        Trip t = lockId == null ? null : lockForAssignment(lockId);
        Shipment s = shipments.findForUpdate(shipmentId)
                .orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (s.getLuggage() != null) {
            throw new BadRequestException("Remove the package from luggage before assigning it directly to a trip");
        }
        if (tripId == null) {
            if (s.getTrip() == null) return s;
            trackingEventService.record(s, ShipmentStatus.READY_FOR_CONSOLIDATION, "UNASSIGNED_FROM_TRIP",
                    "Package removed from trip", "Alexandria, VA", performedBy);
            s.setTrip(null);
            s.setStatus(ShipmentStatus.READY_FOR_CONSOLIDATION);
            shipments.save(s);
            return s;
        }
        if (s.getTrip() != null) {
            if (s.getTrip().getId().equals(tripId)) return s;
            throw new BadRequestException("Remove the package from its current trip before reassigning");
        }
        if (!s.getStatus().canAssignToTransport()) {
            throw new BadRequestException("Package status is not eligible for assignment");
        }
        if (t == null || !t.getId().equals(tripId)) {
            throw new BadRequestException("Package assignment changed; refresh and try again");
        }
        checkCapacity(t, assignmentWeight(s));
        s.setTrip(t);
        s.setStatus(ShipmentStatus.ASSIGNED_TO_TRIP);
        shipments.save(s);
        trackingEventService.record(s, s.getStatus(), "ASSIGNED_TO_TRIP",
                "Package assigned directly to trip " + t.getTripNumber(), "Alexandria, VA", performedBy);
        return s;
    }

    @Transactional(readOnly = true)
    public List<Trip> listForUser(AuthPrincipal p) {
        return p.role() == Role.SENDER ? List.of()
                : trips.findAll().stream().filter(t -> access.canRead(p, t)).toList();
    }

    @Transactional
    public Trip updateStatus(UUID id, TripStatus status, User performedBy) {
        Trip t = trips.findForUpdate(id).orElseThrow(() -> new NotFoundException("Trip not found"));
        t.setStatus(status);
        trips.save(t);
        // Propagate semantics to contained shipments where applicable
        if (status == TripStatus.DEPARTED) {
            for (Luggage l : luggage.findAllByTripId(id)) {
                l.setStatus(LuggageStatus.IN_TRANSIT);
                luggage.save(l);
            }
        } else if (status == TripStatus.ARRIVED) {
            for (Luggage l : luggage.findAllByTripId(id)) {
                l.setStatus(LuggageStatus.ARRIVED_ETHIOPIA);
                luggage.save(l);
            }
        }
        ShipmentStatus mapped = status == TripStatus.DEPARTED ? ShipmentStatus.DEPARTED_US
                : status == TripStatus.ARRIVED ? ShipmentStatus.ARRIVED_ETHIOPIA : null;
        if (mapped != null) {
            for (Shipment s : shipments.findAllOnTrip(id)) {
                s.setStatus(mapped);
                shipments.save(s);
                trackingEventService.record(s, mapped, mapped.name(),
                        status == TripStatus.DEPARTED ? "Departed USA" : "Arrived in Ethiopia",
                        status == TripStatus.DEPARTED ? "USA" : "Addis Ababa", performedBy);
            }
        }
        return t;
    }
}
