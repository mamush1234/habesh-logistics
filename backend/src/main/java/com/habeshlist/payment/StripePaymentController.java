package com.habeshlist.payment;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class StripePaymentController {
    private final StripePayments payments;
    public StripePaymentController(StripePayments payments) { this.payments = payments; }

    @GetMapping("/api/marketplace/requests/{id}/payment")
    @PreAuthorize("hasRole('SENDER')")
    public StripePayments.PaymentDto status(@PathVariable UUID id) { return payments.status(id); }

    @PostMapping("/api/marketplace/requests/{id}/payment/intent")
    @PreAuthorize("hasRole('SENDER')")
    public ResponseEntity<StripePayments.IntentDto> intent(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(payments.start(id));
    }

    @PostMapping("/api/payments/stripe/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        payments.webhook(payload, signature);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/admin/stripe-test-payments")
    @PreAuthorize("hasRole('ADMIN')")
    public StripePayments.AdminPage list(@RequestParam(defaultValue = "0") int page) { return payments.adminList(page); }
}
