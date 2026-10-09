package com.habeshlist.service;

import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.*;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.*;
import com.habeshlist.security.AuthPrincipal;
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

    private static final AtomicInteger TRIP_SEQ = new AtomicInteger(44);
    private static final AtomicInteger LUG_SEQ = new AtomicInteger(0);

    public TripService(TripRepository trips, UserRepository users, LuggageRepository luggage,
                       ShipmentRepository shipments, TrackingEventService trackingEventService) {
        this.trips = trips;
        this.users = users;
        this.luggage = luggage;
        this.shipments = shipments;
        this.trackingEventService = trackingEventService;
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
        BigDecimal per = req.totalLuggageCapacity()
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

    @Transactional(readOnly = true)
    public List<Trip> listForUser(AuthPrincipal p) {
        return switch (p.role()) {
            case ADMIN, AGENT -> trips.findAll();
            case TRAVELER -> trips.findAllByTravelerId(p.userId());
            case SENDER -> List.of();
        };
    }

    @Transactional
    public Trip updateStatus(UUID id, TripStatus status, User performedBy) {
        Trip t = getById(id);
        t.setStatus(status);
        trips.save(t);
        // Propagate semantics to contained shipments where applicable
        if (status == TripStatus.DEPARTED) {
            for (Luggage l : t.getLuggageList()) {
                l.setStatus(LuggageStatus.IN_TRANSIT);
                for (Shipment s : shipments.findAllByLuggageId(l.getId())) {
                    s.setStatus(ShipmentStatus.DEPARTED_US);
                    shipments.save(s);
                    trackingEventService.record(s, ShipmentStatus.DEPARTED_US, "DEPARTED_US",
                            "Departed USA", "USA", performedBy);
                }
                luggage.save(l);
            }
        } else if (status == TripStatus.ARRIVED) {
            for (Luggage l : t.getLuggageList()) {
                l.setStatus(LuggageStatus.ARRIVED_ETHIOPIA);
                for (Shipment s : shipments.findAllByLuggageId(l.getId())) {
                    s.setStatus(ShipmentStatus.ARRIVED_ETHIOPIA);
                    shipments.save(s);
                    trackingEventService.record(s, ShipmentStatus.ARRIVED_ETHIOPIA, "ARRIVED_ETHIOPIA",
                            "Arrived in Ethiopia", "Addis Ababa", performedBy);
                }
                luggage.save(l);
            }
        }
        return t;
    }
}
