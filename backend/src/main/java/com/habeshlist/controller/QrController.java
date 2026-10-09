package com.habeshlist.controller;

import com.habeshlist.entity.Luggage;
import com.habeshlist.entity.Shipment;
import com.habeshlist.repository.LuggageRepository;
import com.habeshlist.repository.ShipmentRepository;
import com.habeshlist.service.QrService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/qr")
public class QrController {

    private final QrService qr;
    private final ShipmentRepository shipments;
    private final LuggageRepository luggage;

    public QrController(QrService qr, ShipmentRepository shipments, LuggageRepository luggage) {
        this.qr = qr; this.shipments = shipments; this.luggage = luggage;
    }

    @GetMapping(value = "/shipment/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> shipment(@PathVariable UUID id) {
        Shipment s = shipments.findById(id).orElseThrow();
        return ResponseEntity.ok(qr.png(s.getTrackingNumber(), 300));
    }

    @GetMapping(value = "/tracking/{trackingNumber}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> tracking(@PathVariable String trackingNumber) {
        Shipment s = shipments.findByTrackingNumber(trackingNumber).orElseThrow();
        return ResponseEntity.ok(qr.png(s.getTrackingNumber(), 300));
    }

    @GetMapping(value = "/luggage/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> luggage(@PathVariable UUID id) {
        Luggage l = luggage.findById(id).orElseThrow();
        return ResponseEntity.ok(qr.png(l.getLuggageNumber(), 300));
    }
}
