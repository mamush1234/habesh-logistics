package com.habeshlist.controller;

import com.habeshlist.dto.GuestRequestDtos.*;
import com.habeshlist.service.GuestRequestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class GuestRequestController {
    private final GuestRequestService service;
    private final com.habeshlist.service.GuestShipmentConversionService conversion;
    public GuestRequestController(GuestRequestService service, com.habeshlist.service.GuestShipmentConversionService conversion) {
        this.service = service;
        this.conversion = conversion;
    }
    @PostMapping("/api/admin/guest-requests/{id}/shipment")
    public com.habeshlist.dto.ApiDtos.ShipmentDto convert(@PathVariable UUID id, @Valid @RequestBody ConvertInput input) {
        return conversion.convert(id, input);
    }
    @PostMapping("/api/admin/guest-request-links")
    public CreatedLinkDto create(@Valid @RequestBody CreateLinkInput input) { return service.create(input); }
    @GetMapping("/api/admin/guest-request-links")
    public List<LinkDto> list() { return service.list(); }
    @PostMapping("/api/admin/guest-request-links/{id}/revoke")
    public LinkDto revoke(@PathVariable UUID id) { return service.revoke(id); }
    @PostMapping("/api/guest-requests/check")
    public GuestStatusDto check(@Valid @RequestBody TokenInput input) { return service.check(input); }
    @PostMapping("/api/guest-requests/submit")
    public GuestStatusDto submit(@Valid @RequestBody SubmitInput input) { return service.submit(input); }
}
