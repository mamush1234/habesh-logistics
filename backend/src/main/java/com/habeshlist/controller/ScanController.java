package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.Luggage;
import com.habeshlist.entity.ScanAudit;
import com.habeshlist.entity.Shipment;
import com.habeshlist.entity.User;
import com.habeshlist.repository.LuggageRepository;
import com.habeshlist.repository.ScanAuditRepository;
import com.habeshlist.repository.ShipmentRepository;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.security.AuthPrincipal;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.security.ShipmentAccess;
import com.habeshlist.service.DtoMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/scan")
@PreAuthorize("hasAnyRole('ADMIN','AGENT','TRAVELER')")
public class ScanController {

    private final ShipmentRepository shipments;
    private final LuggageRepository luggageRepo;
    private final ScanAuditRepository audits;
    private final UserRepository users;
    private final DtoMapper mapper;
    private final ShipmentAccess access;

    public ScanController(ShipmentRepository shipments, LuggageRepository luggageRepo,
                          ScanAuditRepository audits, UserRepository users, DtoMapper mapper,
                          ShipmentAccess access) {
        this.shipments = shipments;
        this.luggageRepo = luggageRepo;
        this.audits = audits;
        this.users = users;
        this.mapper = mapper;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<?> scan(@Valid @RequestBody RequestDtos.ScanRequest req) {
        AuthPrincipal p = SecurityUtils.currentUser();
        User performed = users.findById(p.userId()).orElseThrow();
        String code = req.code().trim().toUpperCase();
        if (code.startsWith("HL-")) {
            Shipment s = shipments.findByTrackingNumber(code)
                    .orElseThrow(() -> new com.habeshlist.exception.NotFoundException("Shipment not found: " + code));
            ShipmentAccess.assertCanRead(p, s);
            audits.save(ScanAudit.builder().entityType("SHIPMENT").entityIdentifier(code)
                    .action(req.action()).performedBy(performed).build());
            return ResponseEntity.ok(Map.of("type", "SHIPMENT", "shipment", ApiDtos.ShipmentDto.from(s)));
        }
        if (code.startsWith("LUG-")) {
            Luggage l = luggageRepo.findByLuggageNumber(code)
                    .orElseThrow(() -> new com.habeshlist.exception.NotFoundException("Luggage not found: " + code));
            access.assertCanRead(p, l);
            audits.save(ScanAudit.builder().entityType("LUGGAGE").entityIdentifier(code)
                    .action(req.action()).performedBy(performed).build());
            return ResponseEntity.ok(Map.of("type", "LUGGAGE", "luggage", mapper.toLuggageDto(l)));
        }
        throw new com.habeshlist.exception.BadRequestException("Unknown code format");
    }
}
