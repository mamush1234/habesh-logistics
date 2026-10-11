package com.habeshlist.service;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.entity.*;
import com.habeshlist.repository.LuggageRepository;
import com.habeshlist.repository.ShipmentRepository;
import com.habeshlist.repository.TrackingEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import com.habeshlist.security.ShipmentAccess;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.entity.enums.Role;

/** Maps JPA entities to response DTOs. */
@Service
public class DtoMapper {

    private final ShipmentRepository shipments;
    private final TrackingEventRepository events;
    private final LuggageRepository luggageRepo;
    private final ShipmentAccess access;

    public DtoMapper(ShipmentRepository shipments, TrackingEventRepository events, LuggageRepository luggageRepo,
                     ShipmentAccess access) {
        this.shipments = shipments;
        this.events = events;
        this.luggageRepo = luggageRepo;
        this.access = access;
    }

    public ApiDtos.LuggageDto toLuggageDto(Luggage l) {
        var user = SecurityUtils.currentUser();
        access.assertCanRead(user, l);
        List<Shipment> contents = shipments.findAllByLuggageId(l.getId());
        List<Shipment> visible = contents.stream()
                .filter(s -> ShipmentAccess.canRead(user, s)).toList();
        BigDecimal assigned = contents.stream()
                .map(s -> s.getDeclaredWeightKg() == null ? BigDecimal.ZERO : s.getDeclaredWeightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = l.getCapacityKg().subtract(assigned);
        List<ApiDtos.ShipmentSummaryDto> items = visible.stream()
                .map(ApiDtos.ShipmentSummaryDto::from).toList();
        return new ApiDtos.LuggageDto(
                l.getId(), l.getLuggageNumber(),
                l.getTrip() == null ? null : l.getTrip().getId(),
                l.getTrip() == null ? null : l.getTrip().getTripNumber(),
                l.getLuggageType(), l.getCapacityKg(), assigned, available,
                l.getActualWeightKg(),
                l.getStatus().name(), l.getSealNumber(), user.role() == Role.ADMIN ? l.getNotes() : null,
                l.getAssignedAgent() == null ? null : l.getAssignedAgent().getId(),
                l.getAssignedAgent() == null ? null : l.getAssignedAgent().getFirstName() + " " + l.getAssignedAgent().getLastName(),
                l.getAssignedTraveler() == null ? null : l.getAssignedTraveler().getId(),
                l.getAssignedTraveler() == null ? null : l.getAssignedTraveler().getFirstName() + " " + l.getAssignedTraveler().getLastName(),
                items);
    }

    @Transactional(readOnly = true)
    public ApiDtos.TripDto toTripDto(Trip t) {
        var user = SecurityUtils.currentUser();
        access.assertCanRead(user, t);
        List<ApiDtos.LuggageDto> luggageDtos = luggageRepo.findAllByTripId(t.getId()).stream()
                .filter(l -> access.canRead(user, l)).map(this::toLuggageDto).toList();
        List<ApiDtos.ShipmentSummaryDto> direct = shipments.findAllByTripId(t.getId()).stream()
                .filter(s -> ShipmentAccess.canRead(user, s))
                .map(ApiDtos.ShipmentSummaryDto::from).toList();
        BigDecimal assignedTotal = shipments.findAllOnTrip(t.getId()).stream()
                .map(s -> s.getDeclaredWeightKg() == null ? BigDecimal.ZERO : s.getDeclaredWeightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal availableTotal = t.getTotalLuggageCapacity().subtract(assignedTotal);
        return new ApiDtos.TripDto(
                t.getId(), t.getTripNumber(),
                t.getTraveler().getId(),
                t.getTraveler().getFirstName() + " " + t.getTraveler().getLastName(),
                t.getDepartureAirport(), t.getArrivalAirport(),
                t.getDepartureDate(), t.getExpectedArrivalDate(),
                t.getTotalLuggageCapacity(), luggageDtos.size(),
                t.getStatus().name(), user.role() == Role.ADMIN ? t.getNotes() : null,
                assignedTotal, availableTotal, luggageDtos, direct);
    }

    public ApiDtos.PickupDto toPickupDto(Pickup p) {
        return new ApiDtos.PickupDto(p.getId(),
                p.getShipment().getId(), p.getShipment().getTrackingNumber(),
                p.getAddress(), p.getDate(), p.getTimeWindow(),
                p.getStatus().name(), p.getNotes(), p.getCompletedAt(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public ApiDtos.PublicTrackingDto toPublicTrackingDto(Shipment s) {
        List<ApiDtos.TrackingEventDto> timeline = events.findAllByShipmentIdOrderByCreatedAtAsc(s.getId())
                .stream().map(ApiDtos.TrackingEventDto::from).toList();
        return new ApiDtos.PublicTrackingDto(
                s.getTrackingNumber(), s.getDestinationCity(), s.getDestinationCountry(),
                s.getDeclaredWeightKg(), s.getStatus().name(),
                s.getCreatedAt(), s.getUpdatedAt(), timeline);
    }
}
