package com.habeshlist.confirmation;

import com.habeshlist.confirmation.ConfirmationDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace/requests/{id}/delivery-confirmation")
public class DeliveryConfirmationController {
    private final DeliveryConfirmationService service;
    public DeliveryConfirmationController(DeliveryConfirmationService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<Status> status(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(id));
    }
    @PostMapping("/code")
    @PreAuthorize("hasRole('SENDER')")
    public ResponseEntity<Issued> issue(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.issue(id));
    }
    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Status> verify(@PathVariable UUID id, @Valid @RequestBody Input input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.verify(id, input));
    }
}
