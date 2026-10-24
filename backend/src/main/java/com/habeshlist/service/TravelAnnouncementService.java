package com.habeshlist.service;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.TravelAnnouncement;
import com.habeshlist.entity.Shipment;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.entity.enums.ShipmentStatus;
import com.habeshlist.entity.enums.TravelBookingStatus;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.TravelAnnouncementRepository;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.repository.ShipmentRepository;
import com.habeshlist.security.AuthPrincipal;
import com.habeshlist.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class TravelAnnouncementService {
    private final TravelAnnouncementRepository announcements;
    private final UserRepository users;
    private final ShipmentRepository shipments;
    private final TrackingEventService tracking;

    public TravelAnnouncementService(TravelAnnouncementRepository announcements, UserRepository users,
                                     ShipmentRepository shipments, TrackingEventService tracking) {
        this.announcements = announcements;
        this.users = users;
        this.shipments = shipments;
        this.tracking = tracking;
    }

    @Transactional
    public ApiDtos.TravelAnnouncementDto create(RequestDtos.CreateTravelAnnouncementRequest req) {
        if (req.expectedArrivalDate() != null && req.expectedArrivalDate().isBefore(req.departureDate())) {
            throw new BadRequestException("Arrival date cannot be before departure date");
        }
        var author = users.findById(SecurityUtils.currentUser().userId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        var announcement = TravelAnnouncement.builder()
                .postedBy(author)
                .origin(req.origin().trim())
                .destination(req.destination().trim())
                .departureDate(req.departureDate())
                .expectedArrivalDate(req.expectedArrivalDate())
                .notes(req.notes() == null ? null : req.notes().trim())
                .totalCapacityKg(req.totalCapacityKg())
                .numberOfLuggage(req.numberOfLuggage())
                .build();
        return toDto(announcements.save(announcement));
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.TravelAnnouncementDto> list() {
        return announcements.findAllByOrderByDepartureDateAscCreatedAtAsc().stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.PublicTravelDto> publicTravel() {
        return announcements.findAllByOrderByDepartureDateAscCreatedAtAsc().stream()
                .filter(a -> !a.getDepartureDate().isBefore(LocalDate.now()) && a.getPostedBy().isActive()
                        && a.getTotalCapacityKg() != null)
                .map(a -> new ApiDtos.PublicTravelDto(a.getId(), a.getOrigin(), a.getDestination(),
                        a.getDepartureDate(), a.getTotalCapacityKg()
                        .subtract(shipments.travelWeight(a.getId(), TravelBookingStatus.APPROVED))))
                .filter(a -> a.availableCapacityKg().signum() > 0).limit(6).toList();
    }

    private ApiDtos.TravelAnnouncementDto toDto(TravelAnnouncement announcement) {
        var author = announcement.getPostedBy();
        BigDecimal approved = shipments.travelWeight(announcement.getId(), TravelBookingStatus.APPROVED);
        return new ApiDtos.TravelAnnouncementDto(announcement.getId(),
                author.getFirstName() + " " + author.getLastName(),
                announcement.getOrigin(), announcement.getDestination(), announcement.getDepartureDate(),
                announcement.getExpectedArrivalDate(), announcement.getNotes(), announcement.getCreatedAt(),
                author.getId(), announcement.getTotalCapacityKg(), announcement.getNumberOfLuggage(), approved,
                announcement.getTotalCapacityKg() == null ? null : announcement.getTotalCapacityKg().subtract(approved));
    }

    public void requestSpace(Shipment shipment, UUID announcementId) {
        if (announcementId == null) return;
        TravelAnnouncement announcement = announcements.findById(announcementId)
                .orElseThrow(() -> new NotFoundException("Travel announcement not found"));
        assertOpen(announcement);
        shipment.setTravelAnnouncement(announcement);
        shipment.setTravelBookingStatus(TravelBookingStatus.PENDING);
    }

    private void assertOpen(TravelAnnouncement announcement) {
        if (announcement.getDepartureDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("This travel has already departed");
        }
        if (announcement.getTotalCapacityKg() == null) {
            throw new BadRequestException("The agent must set travel capacity before accepting requests");
        }
    }

    private void assertOwner(AuthPrincipal principal, TravelAnnouncement announcement) {
        if (principal.role() != Role.ADMIN && !(principal.role() == Role.AGENT
                && announcement.getPostedBy().getId().equals(principal.userId()))) {
            throw new AccessDeniedException("Only the posting agent or an admin can manage this travel");
        }
    }

    @Transactional
    public ApiDtos.TravelAnnouncementDto updateCapacity(UUID id, RequestDtos.UpdateTravelCapacityRequest req) {
        TravelAnnouncement announcement = announcements.findForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Travel announcement not found"));
        assertOwner(SecurityUtils.currentUser(), announcement);
        BigDecimal approved = shipments.travelWeight(id, TravelBookingStatus.APPROVED);
        if (req.totalCapacityKg().compareTo(approved) < 0) {
            throw new BadRequestException("Capacity cannot be less than the approved weight: " + approved + " kg");
        }
        announcement.setTotalCapacityKg(req.totalCapacityKg());
        announcement.setNumberOfLuggage(req.numberOfLuggage());
        return toDto(announcements.save(announcement));
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.TravelBookingRequestDto> requests() {
        AuthPrincipal principal = SecurityUtils.currentUser();
        return shipments.findTravelRequests(principal.role() == Role.ADMIN ? null : principal.userId())
                .stream().map(this::toRequestDto).toList();
    }

    @Transactional
    public ApiDtos.TravelBookingRequestDto decide(UUID announcementId, UUID shipmentId, String decision) {
        // Serialize approvals and capacity edits on the travel before locking a shipment.
        TravelAnnouncement announcement = announcements.findForUpdate(announcementId)
                .orElseThrow(() -> new NotFoundException("Travel announcement not found"));
        AuthPrincipal principal = SecurityUtils.currentUser();
        assertOwner(principal, announcement);
        Shipment shipment = shipments.findForUpdate(shipmentId)
                .orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (shipment.getTravelAnnouncement() == null
                || !shipment.getTravelAnnouncement().getId().equals(announcementId)) {
            throw new BadRequestException("Shipment did not request this travel");
        }
        TravelBookingStatus status = TravelBookingStatus.valueOf(decision);
        if (status != TravelBookingStatus.APPROVED && status != TravelBookingStatus.REJECTED) {
            throw new BadRequestException("Choose APPROVED or REJECTED");
        }
        if (shipment.getTravelBookingStatus() == status) return toRequestDto(shipment);
        if (shipment.getTravelBookingStatus() != TravelBookingStatus.PENDING
                || shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new BadRequestException("Only pending requests can be approved or rejected");
        }
        if (status == TravelBookingStatus.APPROVED) {
            assertOpen(announcement);
            BigDecimal available = announcement.getTotalCapacityKg()
                    .subtract(shipments.travelWeight(announcementId, TravelBookingStatus.APPROVED));
            if (shipment.getDeclaredWeightKg().compareTo(available) > 0) {
                throw new BadRequestException("Not enough travel space. Available: " + available + " kg");
            }
        }
        shipment.setTravelBookingStatus(status);
        shipments.save(shipment);
        var actor = users.findById(principal.userId()).orElseThrow(() -> new NotFoundException("User not found"));
        tracking.record(shipment, shipment.getStatus(), "TRAVEL_REQUEST_" + status.name(),
                "Travel request " + status.name().toLowerCase() + ": " + announcement.getOrigin()
                        + " to " + announcement.getDestination() + " on " + announcement.getDepartureDate(),
                announcement.getOrigin(), actor);
        return toRequestDto(shipment);
    }

    private ApiDtos.TravelBookingRequestDto toRequestDto(Shipment shipment) {
        var announcement = shipment.getTravelAnnouncement();
        boolean reveal = com.habeshlist.security.ShipmentAccess.canRead(SecurityUtils.currentUser(), shipment);
        return new ApiDtos.TravelBookingRequestDto(shipment.getId(), shipment.getTrackingNumber(),
                reveal ? shipment.senderDisplayName() : "Hidden until service fee proof is uploaded",
                shipment.getDeclaredWeightKg(), announcement.getId(),
                announcement.getOrigin() + " → " + announcement.getDestination(),
                announcement.getDepartureDate(), shipment.getTravelBookingStatus().name());
    }
}
