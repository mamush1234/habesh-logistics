package com.habeshlist.service;

import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ShipmentRequestExpiryJob {
    private final ShipmentRequestService requests;
    public ShipmentRequestExpiryJob(ShipmentRequestService requests) { this.requests = requests; }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void expire() { requests.expirePending(); }
}
