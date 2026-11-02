package com.habeshlist.controller;

import com.habeshlist.dto.ShipmentRequestDtos.*;
import com.habeshlist.service.ShipmentRequestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/marketplace/shipment-requests")
public class ShipmentRequestController {
    private final ShipmentRequestService service;
    public ShipmentRequestController(ShipmentRequestService service) { this.service = service; }
    @GetMapping("/quote")
    public QuoteDto quote(@RequestParam UUID packageId, @RequestParam UUID tripId) {
        return service.quote(packageId, tripId);
    }
    @PostMapping
    public RequestDto submit(@Valid @RequestBody SubmitInput input) { return service.submit(input); }
    @GetMapping
    public List<RequestDto> list(@RequestParam UUID packageId) { return service.forPackage(packageId); }
    @GetMapping("/inbox")
    public List<RequestDto> inbox() { return service.inbox(); }
    @PostMapping("/{id}/decision")
    public RequestDto decide(@PathVariable UUID id, @Valid @RequestBody DecisionInput input) {
        return service.decide(id, input);
    }
}
