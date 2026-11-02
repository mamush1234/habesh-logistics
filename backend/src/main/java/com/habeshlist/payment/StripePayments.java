package com.habeshlist.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.habeshlist.dto.ShipmentRequestDtos.QuoteDto;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.MarketplaceTypes.*;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.*;
import com.habeshlist.repository.*;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.service.PackageNotificationService;
import com.stripe.exception.*;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.*;

@Service
public class StripePayments {
    public record PaymentDto(UUID packageId, StripePayment.Status status, QuoteDto quote,
            boolean configured, boolean canPay, String publishableKey, boolean testMode) {}
    public record IntentDto(String clientSecret, String publishableKey, boolean testMode) {}
    public record AdminRow(UUID id, UUID packageId, StripePayment.Status status, long amountCents,
            String currency, java.time.Instant createdAt, java.time.Instant updatedAt, boolean testMode) {}
    public record AdminPage(List<AdminRow> items, int page, int totalPages, long totalElements) {}

    private final DeliveryRequestRepository packages;
    private final ShipmentRequestRepository shipments;
    private final StripePaymentRepository payments;
    private final DeliveryEventRepository events;
    private final PackageNotificationService notifications;
    private final StripeGateway gateway;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public StripePayments(DeliveryRequestRepository packages, ShipmentRequestRepository shipments,
            StripePaymentRepository payments, DeliveryEventRepository events, PackageNotificationService notifications,
            StripeGateway gateway, ObjectMapper json, Clock clock, PlatformTransactionManager transactions) {
        this.packages = packages;
        this.shipments = shipments;
        this.payments = payments;
        this.events = events;
        this.notifications = notifications;
        this.gateway = gateway;
        this.json = json;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    private DeliveryRequest lock(UUID id) {
        DeliveryRequest p = packages.findForUpdate(id).orElseThrow(() -> new NotFoundException("Package not found"));
        // OpenEntityManagerInView can retain entities between the two start transactions.
        entityManager.refresh(p, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return p;
    }

    private void owner(DeliveryRequest p) {
        var actor = SecurityUtils.currentUser();
        if (actor.role() != Role.SENDER || p.getSender() == null || !p.getSender().getId().equals(actor.userId()))
            throw new AccessDeniedException("Only this package's sender can pay");
    }

    private ShipmentRequest accepted(DeliveryRequest p) {
        if (p.getAcceptedOffer() == null) throw new BadRequestException("The shipment request must be accepted before payment");
        return shipments.findByAcceptedOfferId(p.getAcceptedOffer().getId())
                .orElseThrow(() -> new BadRequestException("Stripe test payments are available for accepted targeted requests only"));
    }

    private String agreedQuote(ShipmentRequest s) {
        return s.getProposedQuoteJson() == null ? s.getQuoteJson() : s.getProposedQuoteJson();
    }

    private QuoteDto quote(String value) {
        try {
            return json.readValue(value, QuoteDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read payment quote", e);
        }
    }

    @Transactional(readOnly = true)
    public PaymentDto status(UUID id) {
        DeliveryRequest p = packages.findById(id).orElseThrow(() -> new NotFoundException("Package not found"));
        owner(p);
        ShipmentRequest s = accepted(p);
        StripePayment payment = payments.findByPackageRequestId(id).orElse(null);
        return new PaymentDto(id, payment == null ? null : payment.getStatus(),
                quote(payment == null ? agreedQuote(s) : payment.getQuoteJson()), gateway.configured(),
                p.getStatus() == DeliveryStatus.MATCHED && (payment == null || payment.getStatus() == StripePayment.Status.PENDING),
                gateway.configured() ? gateway.publishableKey() : null, true);
    }

    public IntentDto start(UUID id) {
        // Commit the attempt first, so retries keep the same Stripe idempotency key even after a timeout.
        tx.executeWithoutResult(ignored -> {
            DeliveryRequest p = lock(id);
            owner(p);
            gateway.publishableKey();
            ShipmentRequest s = accepted(p);
            requirePayable(p);
            if (payments.findByPackageRequestId(id).isEmpty()) {
                String savedQuote = agreedQuote(s);
                long cents = quote(savedQuote).total().movePointRight(2).longValueExact();
                if (cents < 50 || cents > 99999999)
                    throw new BadRequestException("Stripe USD test payments must be between $0.50 and $999,999.99");
                payments.save(StripePayment.builder().id(UUID.randomUUID()).packageRequest(p).shipmentRequest(s)
                        .status(StripePayment.Status.PENDING).amountCents(cents).quoteJson(savedQuote)
                        .createdAt(clock.instant()).updatedAt(clock.instant()).build());
            }
        });
        return tx.execute(ignored -> {
            DeliveryRequest p = lock(id);
            owner(p);
            requirePayable(p);
            StripePayment payment = payments.findByPackageRequestId(id).orElseThrow();
            entityManager.refresh(payment);
            if (payment.getStatus() != StripePayment.Status.PENDING)
                throw new BadRequestException("This payment is no longer payable");
            try {
                PaymentIntent intent = ensureIntent(payment);
                if (!Set.of("requires_payment_method", "requires_confirmation", "requires_action").contains(intent.getStatus()))
                    throw new BadRequestException("Payment is processing or complete. Wait for webhook confirmation; do not pay again.");
                if (intent.getClientSecret() == null || intent.getClientSecret().isBlank())
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe did not return a usable test card session. Retry safely.");
                return new IntentDto(intent.getClientSecret(), gateway.publishableKey(), true);
            } catch (StripeException e) {
                throw unavailable(e);
            }
        });
    }

    private void requirePayable(DeliveryRequest p) {
        if (p.getStatus() != DeliveryStatus.MATCHED || p.getEscrowStatus() != EscrowStatus.NONE)
            throw new BadRequestException("Payment is only available after acceptance and before pickup or cancellation");
    }

    private PaymentIntent ensureIntent(StripePayment payment) throws StripeException {
        if (payment.getIntentId() == null && payment.getCreatedAt().isBefore(clock.instant().minusSeconds(23 * 3600)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment setup is too old to retry safely. Contact an administrator for reconciliation.");
        PaymentIntent intent = payment.getIntentId() == null ? gateway.create(payment) : gateway.retrieve(payment.getIntentId());
        validate(payment, intent);
        payment.setIntentId(intent.getId());
        payment.setUpdatedAt(clock.instant());
        return intent;
    }

    private void validate(StripePayment p, PaymentIntent intent) {
        if (!Boolean.FALSE.equals(intent.getLivemode()) || !Long.valueOf(p.getAmountCents()).equals(intent.getAmount())
                || !"usd".equals(intent.getCurrency()) || intent.getMetadata() == null
                || !p.getId().toString().equals(intent.getMetadata().get("habesh_payment_id"))
                || !p.getPackageRequest().getId().toString().equals(intent.getMetadata().get("package_id"))
                || (p.getIntentId() != null && !p.getIntentId().equals(intent.getId())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stripe test payment does not match the saved booking");
    }

    public void webhook(String payload, String signature) {
        if (signature == null) throw new BadRequestException("Missing Stripe signature");
        try {
            Webhook.Signature.verifyHeader(payload, signature, gateway.webhookSecret(), 300);
        } catch (SignatureVerificationException | IllegalArgumentException e) {
            throw new BadRequestException("Invalid Stripe webhook signature or payload");
        }
        JsonNode event;
        try {
            event = json.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid Stripe webhook JSON");
        }
        if (!event.path("livemode").isBoolean() || event.path("livemode").asBoolean())
            throw new BadRequestException("Only Stripe test events are supported");
        if (!Set.of("payment_intent.succeeded", "payment_intent.processing", "payment_intent.payment_failed",
                "payment_intent.canceled").contains(event.path("type").asText())) return;
        String intentId = event.path("data").path("object").path("id").asText();
        if (!intentId.startsWith("pi_")) throw new BadRequestException("Invalid Stripe PaymentIntent");
        try {
            PaymentIntent intent = gateway.retrieve(intentId);
            String paymentId = intent.getMetadata() == null ? null : intent.getMetadata().get("habesh_payment_id");
            if (paymentId == null) return; // Other integrations on the same Stripe test account.
            UUID id;
            try {
                id = UUID.fromString(paymentId);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid payment metadata");
            }
            String packageId = intent.getMetadata().get("package_id");
            UUID bookingId;
            try {
                bookingId = UUID.fromString(packageId);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BadRequestException("Invalid package metadata");
            }
            tx.executeWithoutResult(ignored -> {
                DeliveryRequest p = lock(bookingId);
                StripePayment payment = payments.findById(id).orElseThrow(() -> new NotFoundException("Payment attempt not found"));
                validate(payment, intent);
                payment.setIntentId(intentId);
                if (payment.getStatus() != StripePayment.Status.PENDING) return;
                if ("succeeded".equals(intent.getStatus())) {
                    if (!Long.valueOf(payment.getAmountCents()).equals(intent.getAmountReceived()))
                        throw new BadRequestException("Stripe payment received amount does not match");
                    requirePayable(p);
                    payment.setStatus(StripePayment.Status.SUCCEEDED);
                    p.setStatus(DeliveryStatus.PAID_ESCROW);
                    p.setEscrowStatus(EscrowStatus.STRIPE_TEST_HELD);
                    p.setEscrowAmount(quote(payment.getQuoteJson()).packageAmount());
                    p.setUpdatedAt(clock.instant());
                    events.save(DeliveryEvent.builder().request(p).status(p.getStatus()).actorId(p.getSender().getId())
                            .description("Stripe TEST payment confirmed by verified webhook; no real funds or payout.")
                            .createdAt(clock.instant()).sequenceNumber(events.countByRequestId(p.getId())).build());
                    notifications.notifyRequest(p, p.getSender(), "Stripe TEST payment confirmed. No real money moved.");
                    notifications.notifyRequest(p, p.getAcceptedOffer().getTraveler(), "Stripe TEST payment confirmed for this booking. No real money moved.");
                } else if ("canceled".equals(intent.getStatus())) {
                    payment.setStatus(StripePayment.Status.CANCELLED);
                }
                payment.setUpdatedAt(clock.instant());
            });
        } catch (StripeException e) {
            throw unavailable(e);
        }
    }

    // Called while the marketplace package lock is held, before releasing reserved capacity.
    @Transactional
    public void cancelFor(DeliveryRequest p) {
        StripePayment payment = payments.findByPackageRequestId(p.getId()).orElse(null);
        if (payment == null || payment.getStatus() == StripePayment.Status.REFUNDED
                || payment.getStatus() == StripePayment.Status.CANCELLED) return;
        try {
            PaymentIntent intent = ensureIntent(payment);
            if (!"succeeded".equals(intent.getStatus()) && !"canceled".equals(intent.getStatus())) {
                try {
                    intent = gateway.cancel(intent.getId(), "habesh-test-cancel-" + payment.getId());
                } catch (StripeException e) {
                    intent = gateway.retrieve(intent.getId());
                    if (!"succeeded".equals(intent.getStatus()) && !"canceled".equals(intent.getStatus())) throw e;
                }
                validate(payment, intent);
            }
            if ("succeeded".equals(intent.getStatus())) {
                var refund = gateway.refund(intent.getId(), "habesh-test-refund-" + payment.getId());
                if (!"succeeded".equals(refund.getStatus())
                        || !Long.valueOf(payment.getAmountCents()).equals(refund.getAmount())
                        || !intent.getId().equals(refund.getPaymentIntent()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Test refund is not confirmed. Retry cancellation later.");
                payment.setStatus(StripePayment.Status.REFUNDED);
                p.setEscrowStatus(EscrowStatus.STRIPE_TEST_REFUNDED);
            } else if ("canceled".equals(intent.getStatus())) {
                payment.setStatus(StripePayment.Status.CANCELLED);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Test payment cannot yet be cancelled");
            }
            payment.setUpdatedAt(clock.instant());
        } catch (StripeException e) {
            throw unavailable(e);
        }
    }

    @Transactional(readOnly = true)
    public AdminPage adminList(int page) {
        if (page < 0) throw new BadRequestException("Page must be zero or greater");
        var result = payments.findAllByOrderByCreatedAtDesc(PageRequest.of(page, 20));
        return new AdminPage(result.map(p -> new AdminRow(p.getId(), p.getPackageRequest().getId(), p.getStatus(),
                p.getAmountCents(), "USD", p.getCreatedAt(), p.getUpdatedAt(), true)).getContent(),
                page, result.getTotalPages(), result.getTotalElements());
    }

    private ResponseStatusException unavailable(StripeException e) {
        org.slf4j.LoggerFactory.getLogger(StripePayments.class).warn("Stripe test request failed: {}", e.getCode());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe test service is unavailable. Retry safely; do not submit a second payment.");
    }
}
