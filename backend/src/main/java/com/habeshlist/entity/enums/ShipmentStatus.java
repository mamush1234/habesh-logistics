package com.habeshlist.entity.enums;

public enum ShipmentStatus {
    CREATED,
    PAYMENT_PENDING,
    READY_FOR_PICKUP,
    PICKUP_SCHEDULED,
    PICKED_UP,
    RECEIVED_AT_ALEXANDRIA,
    READY_FOR_CONSOLIDATION,
    ASSIGNED_TO_LUGGAGE,
    ASSIGNED_TO_TRIP,
    HANDED_TO_TRAVELER,
    DEPARTED_US,
    ARRIVED_ETHIOPIA,
    RECEIVED_BY_AGENT,
    READY_FOR_RECIPIENT,
    DELIVERED,
    CANCELLED,
    ON_HOLD;

    public boolean canAssignToTransport() {
        return this == CREATED || this == READY_FOR_PICKUP || this == PICKED_UP
                || this == RECEIVED_AT_ALEXANDRIA || this == READY_FOR_CONSOLIDATION;
    }
}
