package com.habeshlist.service;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.*;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ReferralService {

    private final AgentProfileRepository agentProfiles;
    private final UserRepository users;
    private final CommissionRepository commissions;
    private final ShipmentRepository shipments;

    public ReferralService(AgentProfileRepository agentProfiles, UserRepository users,
                           CommissionRepository commissions, ShipmentRepository shipments) {
        this.agentProfiles = agentProfiles;
        this.users = users;
        this.commissions = commissions;
        this.shipments = shipments;
    }

    @Transactional(readOnly = true)
    public ApiDtos.ReferralStatsDto statsForAgent(UUID agentId) {
        AgentProfile ap = agentProfiles.findById(agentId)
                .orElseThrow(() -> new NotFoundException("Agent profile not found"));
        List<Shipment> referred = shipments.findAllByReferringAgentId(agentId);
        long total = referred.size();
        long delivered = referred.stream().filter(s -> s.getStatus() == ShipmentStatus.DELIVERED).count();
        long active = referred.stream().filter(s ->
                s.getStatus() != ShipmentStatus.DELIVERED &&
                s.getStatus() != ShipmentStatus.CANCELLED).count();
        BigDecimal kg = referred.stream().map(s -> s.getDeclaredWeightKg() == null ? BigDecimal.ZERO : s.getDeclaredWeightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rev = referred.stream().map(s -> s.getTotalAmount() == null ? BigDecimal.ZERO : s.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Commission> cs = commissions.findAllByAgentId(agentId);
        BigDecimal earned = cs.stream().map(Commission::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = cs.stream().filter(c -> c.getStatus() == CommissionStatus.PAID).map(Commission::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = cs.stream().filter(c -> c.getStatus() == CommissionStatus.PENDING).map(Commission::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ApiDtos.ReferralStatsDto(total, active, delivered, kg, rev, earned, paid, pending, ap.getReferralCode());
    }

    @Transactional(readOnly = true)
    public ApiDtos.AdminDashboardDto adminStats() {
        long total = shipments.count();
        long awaiting = shipments.countByStatus(ShipmentStatus.PICKUP_SCHEDULED)
                + shipments.countByStatus(ShipmentStatus.READY_FOR_PICKUP);
        long atAlex = shipments.countByStatus(ShipmentStatus.RECEIVED_AT_ALEXANDRIA);
        long readyCons = shipments.countByStatus(ShipmentStatus.READY_FOR_CONSOLIDATION);
        long assigned = shipments.countByStatus(ShipmentStatus.ASSIGNED_TO_LUGGAGE);
        long travelers = users.findAllByRole(Role.TRAVELER).stream().filter(User::isActive).count();
        long activeTrips = /* status in OPEN,FILLING,READY,HANDED_TO_TRAVELER,DEPARTED */ 0L;
        // compute via count on findAll (small MVP volume):
        activeTrips = 0;
        for (Role r : Role.values()) {} // noop
        // simple
        activeTrips = shipments.findAll().stream()
                .map(Shipment::getLuggage).filter(Objects::nonNull)
                .map(Luggage::getTrip).map(Trip::getId).distinct().count();
        long inTransit = shipments.countByStatus(ShipmentStatus.DEPARTED_US) + shipments.countByStatus(ShipmentStatus.HANDED_TO_TRAVELER);
        long arrived = shipments.countByStatus(ShipmentStatus.ARRIVED_ETHIOPIA) + shipments.countByStatus(ShipmentStatus.RECEIVED_BY_AGENT);
        long pending = shipments.countByStatus(ShipmentStatus.READY_FOR_RECIPIENT);
        BigDecimal rev = shipments.totalRevenue();
        BigDecimal commission = commissions.findAll().stream().map(Commission::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ApiDtos.AdminDashboardDto(total, awaiting, atAlex, readyCons, assigned, travelers,
                activeTrips, inTransit, arrived, pending, rev, commission);
    }
}
