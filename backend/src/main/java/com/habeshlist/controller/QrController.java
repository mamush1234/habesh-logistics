package com.habeshlist.controller;

import com.habeshlist.entity.Luggage;
import com.habeshlist.entity.Shipment;
import com.habeshlist.repository.LuggageRepository;
import com.habeshlist.repository.ShipmentRepository;
import com.habeshlist.service.QrService;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.security.ShipmentAccess;
import com.habeshlist.exception.NotFoundException;
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
    private final ShipmentAccess access;

    public QrController(QrService qr, ShipmentRepository shipments, LuggageRepository luggage,
                        ShipmentAccess access) {
        this.qr = qr; this.shipments = shipments; this.luggage = luggage;
        this.access = access;
    }

    @GetMapping(value = "/shipment/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> shipment(@PathVariable UUID id) {
        Shipment s = shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment not found"));
        ShipmentController.assertCanRead(SecurityUtils.currentUser(), s);
        return ResponseEntity.ok(qr.png(s.getTrackingNumber(), 300));
    }

    @GetMapping(value = "/tracking/{trackingNumber}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> tracking(@PathVariable String trackingNumber) {
        Shipment s = shipments.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new NotFoundException("Shipment not found"));
        ShipmentController.assertCanRead(SecurityUtils.currentUser(), s);
        return ResponseEntity.ok(qr.png(s.getTrackingNumber(), 300));
    }

    @GetMapping(value = "/luggage/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> luggage(@PathVariable UUID id) {
        Luggage l = luggage.findById(id).orElseThrow(() -> new NotFoundException("Luggage not found"));
        access.assertCanRead(SecurityUtils.currentUser(), l);
        return ResponseEntity.ok(qr.png(l.getLuggageNumber(), 300));
    }
}
