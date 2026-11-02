package com.habeshlist;

import com.fasterxml.jackson.databind.*;
import com.habeshlist.admin.*;
import com.habeshlist.payment.*;
import com.habeshlist.repository.*;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:stripe-payments;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StripePaymentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StripePaymentRepository payments;
    @Autowired DeliveryRequestRepository packages;
    @Autowired MarketTripRepository trips;
    @Autowired DeliveryEventRepository events;
    @Autowired PlatformSettingsRepository settings;
    @MockBean StripeGateway gateway;
    final Map<String, PaymentIntent> remote = new ConcurrentHashMap<>();
    final AtomicInteger creates = new AtomicInteger();
    String sender, agent, admin, traveler;

    @BeforeEach
    void setup() throws Exception {
        sender = login("sender@example.com", "Sender@123");
        agent = login("agent@example.com", "Agent@123");
        admin = login("admin@habeshlist.com", "Admin@123");
        traveler = login("traveler@example.com", "Traveler@123");
        settings.save(PlatformSettings.builder().id(PlatformSettings.SINGLETON_ID)
                .platformFeePercent(new BigDecimal("10.00")).agentCommissionPercent(new BigDecimal("10.00"))
                .feePayer(PlatformSettings.FeePayer.SENDER).updatedAt(Instant.now()).build());
        when(gateway.configured()).thenReturn(true);
        when(gateway.publishableKey()).thenReturn("pk_test_fixture");
        when(gateway.webhookSecret()).thenReturn("whsec_fixture");
        when(gateway.create(any())).thenAnswer(invocation -> {
            StripePayment p = invocation.getArgument(0);
            creates.incrementAndGet();
            return remote.computeIfAbsent("pi_" + p.getId(), id -> {
                PaymentIntent intent = new PaymentIntent();
                intent.setId(id);
                intent.setAmount(p.getAmountCents());
                intent.setAmountReceived(0L);
                intent.setCurrency("usd");
                intent.setLivemode(false);
                intent.setStatus("requires_payment_method");
                intent.setClientSecret(id + "_secret_fixture");
                intent.setMetadata(Map.of("habesh_payment_id", p.getId().toString(),
                        "package_id", p.getPackageRequest().getId().toString()));
                return intent;
            });
        });
        when(gateway.retrieve(anyString())).thenAnswer(i -> Objects.requireNonNull(remote.get(i.getArgument(0))));
        when(gateway.cancel(anyString(), anyString())).thenAnswer(i -> {
            PaymentIntent intent = remote.get(i.getArgument(0));
            intent.setStatus("canceled");
            return intent;
        });
        when(gateway.refund(anyString(), anyString())).thenAnswer(i -> {
            PaymentIntent intent = remote.get(i.getArgument(0));
            Refund refund = new Refund();
            refund.setId("re_fixture");
            refund.setStatus("succeeded");
            refund.setAmount(intent.getAmount());
            refund.setPaymentIntent(intent.getId());
            return refund;
        });
    }

    ResultActions call(String method, String path, String token, Object body) throws Exception {
        var request = switch (method) { case "GET" -> get(path); case "PUT" -> put(path); default -> post(path); };
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    JsonNode ok(String method, String path, String token, Object body) throws Exception {
        return json.readTree(call(method, path, token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String login(String email, String password) throws Exception {
        return ok("POST", "/api/auth/login", null, Map.of("email", email, "password", password)).get("token").asText();
    }
    String date(int offset) { return LocalDate.now().plusDays(offset).toString(); }
    String path(UUID id) { return "/api/marketplace/requests/" + id; }
    String payment(UUID id) { return path(id) + "/payment"; }
    JsonNode targeted(boolean accept, boolean propose) throws Exception {
        String origin = "Stripe-" + UUID.randomUUID();
        JsonNode trip = ok("POST", "/api/marketplace/trips", agent, Map.of("origin", origin, "destination", "Addis Ababa",
                "departureDate", date(3), "expectedArrivalDate", date(4), "capacityKg", 5,
                "allowedItems", List.of("CLOTHING"), "blockedItems", List.of(),
                "itemPrices", List.of(Map.of("category", "CLOTHING", "price", 3.25, "unit", "PER_KG"))));
        JsonNode p = ok("POST", "/api/marketplace/requests", sender, Map.of("pickupCity", origin, "destinationCity", "Addis Ababa",
                "availableFrom", date(1), "deadline", date(5), "itemType", "CLOTHING", "description", "Test clothes",
                "weightKg", 4, "offeredPrice", 50));
        JsonNode quote = ok("GET", "/api/marketplace/shipment-requests/quote?packageId=" + p.get("id").asText()
                + "&tripId=" + trip.get("id").asText(), sender, null);
        JsonNode request = ok("POST", "/api/marketplace/shipment-requests", sender, Map.of(
                "packageId", p.get("id").asText(), "tripId", trip.get("id").asText(), "quoteKey", quote.get("quoteKey").asText()));
        String decision = "/api/marketplace/shipment-requests/" + request.get("id").asText() + "/decision";
        if (propose) {
            request = ok("POST", decision, agent, Map.of("action", "PROPOSE_PRICE", "quoteKey", quote.get("quoteKey").asText(), "proposedPrice", 20));
            if (accept) request = ok("POST", decision, sender, Map.of("action", "CONFIRM_PRICE",
                    "quoteKey", request.get("proposedQuote").get("quoteKey").asText()));
        } else if (accept) request = ok("POST", decision, agent, Map.of("action", "ACCEPT", "quoteKey", quote.get("quoteKey").asText()));
        return request;
    }
    UUID packageId(JsonNode request) { return UUID.fromString(request.get("packageId").asText()); }
    PaymentIntent start(UUID id) throws Exception {
        ok("POST", payment(id) + "/intent", sender, Map.of("amount", 1, "currency", "eur"));
        return remote.get(payments.findByPackageRequestId(id).orElseThrow().getIntentId());
    }
    ResultActions webhook(PaymentIntent intent, String type) throws Exception {
        String body = json.writeValueAsString(Map.of("id", "evt_" + UUID.randomUUID(), "object", "event",
                "livemode", false, "type", type, "data", Map.of("object", Map.of("id", intent.getId()))));
        return mvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                .content(body).header("Stripe-Signature", Webhook.Signature.generateSignatureHeader(body, "whsec_fixture")));
    }
    void succeeded(PaymentIntent intent) {
        intent.setStatus("succeeded");
        intent.setAmountReceived(intent.getAmount());
    }
    void cancel(UUID id) throws Exception {
        ok("PUT", path(id) + "/status", sender, Map.of("status", "CANCELLED"));
    }

    @Test
    void onlyOwningSenderCanPayAfterAcceptanceAndNoClientAmountIsTrusted() throws Exception {
        UUID pending = packageId(targeted(false, false));
        call("POST", payment(pending) + "/intent", sender, null).andExpect(status().isBadRequest());
        assertTrue(payments.findByPackageRequestId(pending).isEmpty());
        UUID id = packageId(targeted(true, false));
        for (String token : List.of(agent, admin, traveler)) {
            call("GET", payment(id), token, null).andExpect(status().isForbidden());
            call("POST", payment(id) + "/intent", token, null).andExpect(status().isForbidden());
        }
        call("POST", payment(id) + "/intent", null, null).andExpect(status().isUnauthorized());
        JsonNode signup = ok("POST", "/api/auth/register", null, Map.of("firstName", "Payment" + UUID.randomUUID().toString().substring(0, 8),
                "lastName", "Sender", "email", UUID.randomUUID() + "@example.com", "password", "Payment-password-2026", "role", "SENDER"));
        call("GET", payment(id), signup.get("token").asText(), null).andExpect(status().isForbidden());
        PaymentIntent intent = start(id);
        assertEquals(1430L, intent.getAmount());
        JsonNode view = ok("GET", payment(id), sender, null);
        assertEquals("PENDING", view.get("status").asText());
        assertFalse(view.has("clientSecret"));
        assertFalse(view.has("intentId"));
        assertEquals("MATCHED", packages.findById(id).orElseThrow().getStatus().name());
        call("PUT", path(id) + "/status", sender, Map.of("status", "PAID_ESCROW")).andExpect(status().isBadRequest());
        call("GET", "/api/admin/stripe-test-payments", sender, null).andExpect(status().isForbidden());
        assertFalse(ok("GET", "/api/admin/stripe-test-payments", admin, null).toString().contains("_secret_"));
    }

    @Test
    void retryUsesOneIntentAndAgreedPriceAndFeeSnapshotSurviveSettingChanges() throws Exception {
        UUID id = packageId(targeted(true, true));
        PlatformSettings changed = settings.findById(PlatformSettings.SINGLETON_ID).orElseThrow();
        changed.setPlatformFeePercent(new BigDecimal("25"));
        changed.setFeePayer(PlatformSettings.FeePayer.TRAVELER);
        settings.save(changed);
        PaymentIntent first = start(id);
        PaymentIntent second = start(id);
        assertSame(first, second);
        assertEquals(1, creates.get());
        assertEquals(2200L, first.getAmount());
        JsonNode view = ok("GET", payment(id), sender, null);
        assertEquals(10, view.get("quote").get("platformFeePercent").intValue());
        assertEquals("SENDER", view.get("quote").get("feePayer").asText());
        assertEquals(22, view.get("quote").get("total").intValue());
    }

    @Test
    void signedWebhookIsRequiredAndSuccessIsIdempotentAndAuthoritative() throws Exception {
        UUID id = packageId(targeted(true, false));
        PaymentIntent intent = start(id);
        assertTrue(ok("GET", path(id), sender, null).get("travelerContact").isNull());
        String chat = path(id) + "/messages";
        for (String token : List.of(sender, agent, admin)) {
            call("GET", chat, token, null).andExpect(status().isForbidden());
            call("POST", chat, token, Map.of("body", "Pending payment")).andExpect(status().isForbidden());
        }
        String body = "{\"livemode\":false,\"type\":\"payment_intent.succeeded\"}";
        mvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                .content(body).header("Stripe-Signature", "t=1,v1=invalid")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Stripe-Signature", Webhook.Signature.generateSignatureHeader(body, "whsec_fixture", 1)))
                .andExpect(status().isBadRequest());
        succeeded(intent);
        // Client/provider success alone cannot open chat before the verified webhook.
        call("GET", chat, sender, null).andExpect(status().isForbidden());
        call("POST", chat, sender, Map.of("body", "Webhook not received")).andExpect(status().isForbidden());
        long before = events.countByRequestId(id);
        webhook(intent, "payment_intent.succeeded").andExpect(status().isOk());
        webhook(intent, "payment_intent.succeeded").andExpect(status().isOk());
        webhook(intent, "payment_intent.payment_failed").andExpect(status().isOk());
        assertEquals(before + 1, events.countByRequestId(id));
        JsonNode view = ok("GET", path(id), sender, null);
        assertEquals("PAID_ESCROW", view.get("status").asText());
        assertEquals("STRIPE_TEST_HELD", view.get("escrowStatus").asText());
        assertFalse(view.get("travelerContact").isNull());
        assertTrue(ok("GET", chat, sender, null).get("writable").asBoolean());
        assertTrue(ok("GET", chat, agent, null).get("writable").asBoolean());
        assertFalse(ok("GET", chat, admin, null).get("writable").asBoolean());
        ok("POST", chat, sender, Map.of("body", "Verified payment pickup"));
        assertEquals(1, ok("GET", chat, agent, null).get("messages").size());
        call("POST", chat, admin, Map.of("body", "No admin writes")).andExpect(status().isForbidden());
        call("POST", payment(id) + "/intent", sender, null).andExpect(status().isBadRequest());
        assertEquals("SUCCEEDED", ok("GET", payment(id), sender, null).get("status").asText());
        JsonNode legacy = ok("GET", "/api/admin/console/payments", admin, null);
        assertFalse(legacy.toString().contains(id.toString()));
    }

    @Test
    void amountCurrencyLiveModeAndMetadataMismatchNeverFundABooking() throws Exception {
        UUID id = packageId(targeted(true, false));
        PaymentIntent intent = start(id);
        succeeded(intent);
        intent.setAmount(1L);
        webhook(intent, "payment_intent.succeeded").andExpect(status().isConflict());
        intent.setAmount(1430L);
        intent.setCurrency("eur");
        webhook(intent, "payment_intent.succeeded").andExpect(status().isConflict());
        intent.setCurrency("usd");
        intent.setLivemode(true);
        webhook(intent, "payment_intent.succeeded").andExpect(status().isConflict());
        intent.setLivemode(false);
        intent.setAmountReceived(1L);
        webhook(intent, "payment_intent.succeeded").andExpect(status().isBadRequest());
        assertEquals("MATCHED", packages.findById(id).orElseThrow().getStatus().name());
        assertEquals("PENDING", payments.findByPackageRequestId(id).orElseThrow().getStatus().name());
    }

    @Test
    void pendingCancellationCancelsStripeBeforeReleasingCapacityAndLateWebhookCannotFund() throws Exception {
        JsonNode request = targeted(true, false);
        UUID id = packageId(request);
        PaymentIntent intent = start(id);
        cancel(id);
        assertEquals("canceled", intent.getStatus());
        assertEquals("CANCELLED", payments.findByPackageRequestId(id).orElseThrow().getStatus().name());
        assertEquals(0, trips.findById(UUID.fromString(request.get("tripId").asText())).orElseThrow().getReservedKg().signum());
        succeeded(intent);
        webhook(intent, "payment_intent.succeeded").andExpect(status().isOk());
        assertEquals("CANCELLED", packages.findById(id).orElseThrow().getStatus().name());
        call("GET", path(id) + "/messages", sender, null).andExpect(status().isForbidden());
        verify(gateway, never()).refund(anyString(), anyString());
    }

    @Test
    void cancellationRefundsSuccessfulPaymentEvenBeforeWebhookAndLateSuccessIsIgnored() throws Exception {
        UUID id = packageId(targeted(true, false));
        PaymentIntent intent = start(id);
        succeeded(intent);
        cancel(id);
        assertEquals("REFUNDED", payments.findByPackageRequestId(id).orElseThrow().getStatus().name());
        assertEquals("STRIPE_TEST_REFUNDED", packages.findById(id).orElseThrow().getEscrowStatus().name());
        webhook(intent, "payment_intent.succeeded").andExpect(status().isOk());
        assertEquals("CANCELLED", packages.findById(id).orElseThrow().getStatus().name());
        assertTrue(ok("GET", path(id), sender, null).get("travelerContact").isNull());
        assertFalse(ok("GET", path(id) + "/messages", sender, null).get("writable").asBoolean());
        call("POST", path(id) + "/messages", sender, Map.of("body", "Refunded")).andExpect(status().isBadRequest());
        verify(gateway).refund(eq(intent.getId()), startsWith("habesh-test-refund-"));
    }

    @Test
    void failedRefundKeepsBookingAndCapacityUntilRetryConfirmsRefund() throws Exception {
        JsonNode request = targeted(true, false);
        UUID id = packageId(request);
        PaymentIntent intent = start(id);
        succeeded(intent);
        webhook(intent, "payment_intent.succeeded").andExpect(status().isOk());
        Refund pending = new Refund();
        pending.setStatus("pending");
        doReturn(pending).when(gateway).refund(anyString(), anyString());
        call("PUT", path(id) + "/status", sender, Map.of("status", "CANCELLED")).andExpect(status().isConflict());
        assertEquals("PAID_ESCROW", packages.findById(id).orElseThrow().getStatus().name());
        assertEquals(4, trips.findById(UUID.fromString(request.get("tripId").asText())).orElseThrow().getReservedKg().intValueExact());
        pending.setStatus("succeeded");
        pending.setAmount(1430L);
        pending.setPaymentIntent(intent.getId());
        cancel(id);
        assertEquals("REFUNDED", payments.findByPackageRequestId(id).orElseThrow().getStatus().name());
        assertEquals(0, trips.findById(UUID.fromString(request.get("tripId").asText())).orElseThrow().getReservedKg().signum());
    }

    @Test
    void concurrentStartsUseOneDurableAttemptAndOneIntent() throws Exception {
        UUID id = packageId(targeted(true, false));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> start(id).getId());
            var b = pool.submit(() -> start(id).getId());
            assertEquals(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertEquals(1, creates.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentSuccessWebhooksRecordPaymentOnlyOnce() throws Exception {
        UUID id = packageId(targeted(true, false));
        PaymentIntent intent = start(id);
        succeeded(intent);
        long before = events.countByRequestId(id);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> webhook(intent, "payment_intent.succeeded").andExpect(status().isOk()));
            var b = pool.submit(() -> webhook(intent, "payment_intent.succeeded").andExpect(status().isOk()));
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
            assertEquals(before + 1, events.countByRequestId(id));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void testDeliveryCanCompleteWithoutGeneratingRealCommissionOrPayout() throws Exception {
        UUID id = packageId(targeted(true, false));
        PaymentIntent intent = start(id);
        succeeded(intent);
        webhook(intent, "payment_intent.succeeded").andExpect(status().isOk());
        for (String state : List.of("PICKED_UP", "IN_TRANSIT", "ARRIVED"))
            ok("PUT", path(id) + "/status", agent, Map.of("status", state));
        String code = ok("POST", path(id) + "/delivery-confirmation/code", sender, null).get("code").asText();
        ok("POST", path(id) + "/delivery-confirmation/verify", agent, Map.of("code", code));
        assertEquals("STRIPE_TEST_RELEASED", packages.findById(id).orElseThrow().getEscrowStatus().name());
        assertEquals("SUCCEEDED", payments.findByPackageRequestId(id).orElseThrow().getStatus().name());
        verify(gateway, never()).refund(anyString(), anyString());
    }

    @Test
    void apiTimeoutKeepsDurableAttemptAndRetryUsesSamePaymentIdentity() throws Exception {
        UUID id = packageId(targeted(true, false));
        doThrow(new com.stripe.exception.ApiException("Test timeout", null, "connection_error", 502, null))
                .when(gateway).create(any());
        call("POST", payment(id) + "/intent", sender, null).andExpect(status().isBadGateway());
        StripePayment attempt = payments.findByPackageRequestId(id).orElseThrow();
        assertNull(attempt.getIntentId());
        UUID attemptId = attempt.getId();
        doAnswer(invocation -> {
            StripePayment p = invocation.getArgument(0);
            assertEquals(attemptId, p.getId());
            PaymentIntent intent = new PaymentIntent();
            intent.setId("pi_timeout_fixture");
            intent.setAmount(1430L);
            intent.setAmountReceived(0L);
            intent.setCurrency("usd");
            intent.setLivemode(false);
            intent.setStatus("requires_payment_method");
            intent.setClientSecret("pi_timeout_fixture_secret_fixture");
            intent.setMetadata(Map.of("habesh_payment_id", p.getId().toString(), "package_id", id.toString()));
            remote.put(intent.getId(), intent);
            return intent;
        }).when(gateway).create(any());
        start(id);
        assertEquals(attemptId, payments.findByPackageRequestId(id).orElseThrow().getId());
    }
}
