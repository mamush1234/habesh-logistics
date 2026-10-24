package com.habeshlist.security;

import com.habeshlist.entity.Luggage;
import com.habeshlist.entity.Shipment;
import com.habeshlist.entity.Trip;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.repository.LuggageRepository;
import com.habeshlist.repository.ShipmentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ShipmentAccess {
    private final ShipmentRepository shipments;
    private final LuggageRepository luggage;

    public ShipmentAccess(ShipmentRepository shipments, LuggageRepository luggage) {
        this.shipments = shipments;
        this.luggage = luggage;
    }

    public static boolean canRead(AuthPrincipal user, Shipment shipment) {
        if (shipment.getQueueStatus() != null && user.role() != Role.ADMIN && user.role() != Role.SENDER) {
            return shipment.getQueueStatus() == com.habeshlist.entity.enums.PackageQueueStatus.PAYMENT_UPLOADED
                    && shipment.getQueueClaimant() != null
                    && shipment.getQueueClaimant().getId().equals(user.userId());
        }
        return switch (user.role()) {
            case ADMIN -> true;
            case SENDER -> shipment.getSender() != null && shipment.getSender().getId().equals(user.userId());
            case TRAVELER -> shipment.isCarriedBy(user.userId());
            case AGENT -> (shipment.getReferringAgent() != null
                    && shipment.getReferringAgent().getId().equals(user.userId()))
                    || (shipment.getAssignedAgent() != null
                    && shipment.getAssignedAgent().getId().equals(user.userId()))
                    || (shipment.getLuggage() != null && isAssigned(user, shipment.getLuggage()));
        };
    }

    public static void assertCanRead(AuthPrincipal user, Shipment shipment) {
        if (!canRead(user, shipment)) throw new AccessDeniedException("Not authorized for this shipment");
    }

    public static boolean isAssigned(AuthPrincipal user, Luggage item) {
        return switch (user.role()) {
            case ADMIN -> true;
            case SENDER -> false;
            case AGENT -> item.getAssignedAgent() != null && item.getAssignedAgent().getId().equals(user.userId());
            case TRAVELER -> (item.getTrip() != null && item.getTrip().getTraveler().getId().equals(user.userId()))
                    || (item.getAssignedTraveler() != null && item.getAssignedTraveler().getId().equals(user.userId()));
        };
    }

    public boolean canRead(AuthPrincipal user, Luggage item) {
        return user.role() != Role.SENDER && (isAssigned(user, item)
                || shipments.findAllByLuggageId(item.getId()).stream().anyMatch(s -> canRead(user, s)));
    }

    public void assertCanRead(AuthPrincipal user, Luggage item) {
        if (!canRead(user, item)) throw new AccessDeniedException("Not authorized for this luggage");
    }

    public boolean canRead(AuthPrincipal user, Trip trip) {
        return switch (user.role()) {
            case ADMIN -> true;
            case SENDER -> false;
            case AGENT, TRAVELER -> (user.role() == Role.TRAVELER && trip.getTraveler().getId().equals(user.userId()))
                    || luggage.findAllByTripId(trip.getId()).stream().anyMatch(l -> isAssigned(user, l))
                    || shipments.findAllOnTrip(trip.getId()).stream().anyMatch(s -> canRead(user, s));
        };
    }

    public void assertCanRead(AuthPrincipal user, Trip trip) {
        if (!canRead(user, trip)) throw new AccessDeniedException("Not authorized for this trip");
    }
}
