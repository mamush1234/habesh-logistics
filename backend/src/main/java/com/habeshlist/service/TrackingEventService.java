package com.habeshlist.service;

import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.ShipmentStatus;
import com.habeshlist.repository.TrackingEventRepository;
import org.springframework.stereotype.Service;

@Service
public class TrackingEventService {

    private final TrackingEventRepository events;

    public TrackingEventService(TrackingEventRepository events) {
        this.events = events;
    }

    public void record(Shipment s, ShipmentStatus status, String eventType, String description,
                       String location, User performedBy) {
        TrackingEvent e = TrackingEvent.builder()
                .shipment(s)
                .luggage(s.getLuggage())
                .trip(s.getLuggage() == null ? null : s.getLuggage().getTrip())
                .eventType(eventType)
                .status(status)
                .description(description)
                .location(location)
                .performedBy(performedBy)
                .build();
        events.save(e);
    }
}
