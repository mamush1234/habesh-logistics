package com.habeshlist;

import com.fasterxml.jackson.databind.*;
import com.habeshlist.confirmation.*;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.MarketplaceTypes.*;
import com.habeshlist.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeliveryConfirmationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DeliveryRequestRepository packages;
    @Autowired DeliveryOfferRepository offers;
    @Autowired MarketTripRepository trips;
    @Autowired UserRepository users;
    @Autowired DeliveryConfirmationRepository confirmations;
    @Autowired DeliveryEventRepository events;
    String sender, agent, admin, traveler;

    @BeforeEach
    void setup() throws Exception {
        sender = login("sender@example.com", "Sender@123");
        agent = login("agent@example.com", "Agent@123");
        admin = login("admin@habeshlist.com", "Admin@123");
        traveler = login("traveler@example.com", "Traveler@123");
    }
    String login(String email, String password) throws Exception {
        return ok("POST", "/api/auth/login", null, Map.of("email", email, "password", password)).get("token").asText();
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
    String root(UUID id) { return "/api/marketplace/requests/" + id; }
    String path(UUID id) { return root(id) + "/delivery-confirmation"; }
    UUID booking(boolean travelerCarrier) {
        Instant now = Instant.now();
        User senderUser = users.findByEmailIgnoreCase("sender@example.com").orElseThrow();
        User carrier = users.findByEmailIgnoreCase(travelerCarrier ? "traveler@example.com" : "agent@example.com").orElseThrow();
        MarketTrip trip = trips.saveAndFlush(MarketTrip.builder().traveler(carrier).origin("Code-" + UUID.randomUUID())
                .destination("Addis Ababa").departureDate(LocalDate.now()).capacityKg(BigDecimal.TEN)
                .reservedKg(BigDecimal.ONE).createdAt(now).build());
        DeliveryRequest p = packages.saveAndFlush(DeliveryRequest.builder().sender(senderUser).adminApproved(true)
                .pickupCity(trip.getOrigin()).destinationCity(trip.getDestination()).availableFrom(LocalDate.now())
                .deadline(LocalDate.now().plusDays(5)).itemType(ItemType.CLOTHING).description("Private parcel")
                .weightKg(BigDecimal.ONE).offeredPrice(BigDecimal.TEN).status(DeliveryStatus.ARRIVED)
                .escrowStatus(EscrowStatus.DEMO_HELD).escrowAmount(BigDecimal.TEN).createdAt(now).updatedAt(now).build());
        DeliveryOffer offer = offers.saveAndFlush(DeliveryOffer.builder().request(p).traveler(carrier).trip(trip)
                .price(BigDecimal.TEN).status(OfferStatus.ACCEPTED).createdAt(now).build());
        p.setAcceptedOffer(offer);
        packages.saveAndFlush(p);
        return p.getId();
    }
    String issue(UUID id) throws Exception {
        var response = call("POST", path(id) + "/code", sender, null).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        JsonNode issued = json.readTree(response.getContentAsString());
        assertTrue(issued.get("code").asText().matches("[0-9]{6}"));
        return issued.get("code").asText();
    }
    void state(UUID id, DeliveryStatus status, EscrowStatus escrow) {
        DeliveryRequest p = packages.findById(id).orElseThrow();
        p.setStatus(status);
        p.setEscrowStatus(escrow);
        packages.saveAndFlush(p);
    }

    @Test
    void validCodeCompletesOnceWithTwoEventsAndNoCodeInReadModels() throws Exception {
        UUID id = booking(false);
        String code = issue(id);
        assertNotEquals(code, confirmations.findById(id).orElseThrow().getCodeHash());
        String statusBody = ok("GET", path(id), agent, null).toString();
        assertFalse(statusBody.contains(code));
        assertFalse(statusBody.contains("codeHash"));
        call("PUT", root(id) + "/status", agent, Map.of("status", "DELIVERED")).andExpect(status().isBadRequest());
        call("PUT", root(id) + "/status", sender, Map.of("status", "COMPLETED")).andExpect(status().isBadRequest());
        JsonNode result = ok("POST", path(id) + "/verify", agent, Map.of("code", code));
        assertFalse(result.get("confirmedAt").isNull());
        DeliveryRequest stored = packages.findById(id).orElseThrow();
        assertEquals(DeliveryStatus.COMPLETED, stored.getStatus());
        assertEquals(EscrowStatus.DEMO_RELEASED, stored.getEscrowStatus());
        JsonNode view = ok("GET", root(id), sender, null);
        assertEquals(List.of("DELIVERED", "COMPLETED"), view.get("timeline").findValues("status").stream().map(JsonNode::asText).toList());
        assertFalse(view.toString().contains(code));
        assertFalse(ok("GET", "/api/package-queue/notifications", sender, null).toString().contains(code));
        assertTrue(ok("GET", root(id) + "/reviews", sender, null).get("canReview").asBoolean());
        assertFalse(ok("GET", root(id) + "/messages", agent, null).get("writable").asBoolean());
        call("POST", path(id) + "/verify", agent, Map.of("code", code)).andExpect(status().isBadRequest());
        call("POST", path(id) + "/code", sender, null).andExpect(status().isBadRequest());
        assertEquals(2, events.countByRequestId(id));
    }

    @Test
    void codeIsSenderOnlyAndTravelersRemainReadOnlyEvenWhenSelected() throws Exception {
        UUID id = booking(true);
        for (String token : List.of(agent, admin, traveler))
            call("POST", path(id) + "/code", token, null).andExpect(status().isForbidden());
        String code = issue(id);
        assertFalse(ok("GET", path(id), traveler, null).get("canVerify").asBoolean());
        for (String token : List.of(sender, traveler, agent))
            call("POST", path(id) + "/verify", token, Map.of("code", code)).andExpect(status().isForbidden());
        String outsider = login("sender2@example.com", "Sender@123");
        call("GET", path(id), outsider, null).andExpect(status().isForbidden());
        call("POST", path(id) + "/code", outsider, null).andExpect(status().isForbidden());
        call("GET", path(id), null, null).andExpect(status().isUnauthorized());
        call("POST", path(id) + "/verify", null, Map.of("code", code)).andExpect(status().isUnauthorized());
        var stored = packages.findById(id).orElseThrow();
        stored.setAdminApproved(false);
        packages.saveAndFlush(stored);
        call("GET", path(id), traveler, null).andExpect(status().isForbidden());
        ok("POST", path(id) + "/verify", admin, Map.of("code", code));
    }

    @Test
    void incorrectAttemptsPersistAcrossRequestsAndLockBeforeIssuanceCanResetThem() throws Exception {
        UUID id = booking(false);
        String code = issue(id), wrong = code.equals("000000") ? "111111" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            call("POST", path(id) + "/verify", agent, Map.of("code", wrong)).andExpect(status().isBadRequest());
            assertEquals(attempt, confirmations.findById(id).orElseThrow().getFailedAttempts());
        }
        call("POST", path(id) + "/verify", agent, Map.of("code", code)).andExpect(status().isBadRequest());
        call("POST", path(id) + "/code", sender, null).andExpect(status().isTooManyRequests());
        assertFalse(ok("GET", path(id), agent, null).get("canVerify").asBoolean());
        assertEquals(DeliveryStatus.ARRIVED, packages.findById(id).orElseThrow().getStatus());
        assertEquals(0, events.countByRequestId(id));
    }

    @Test
    void expiryReissueCooldownAndHourlyLimitAreEnforced() throws Exception {
        UUID id = booking(false);
        String old = issue(id);
        call("POST", path(id) + "/code", sender, null).andExpect(status().isTooManyRequests());
        DeliveryConfirmation c = confirmations.findById(id).orElseThrow();
        c.setIssuedAt(Instant.now().minusSeconds(1000));
        c.setExpiresAt(Instant.now().minusSeconds(1));
        confirmations.saveAndFlush(c);
        call("POST", path(id) + "/verify", agent, Map.of("code", old)).andExpect(status().isBadRequest());
        String replacement = issue(id);
        c = confirmations.findById(id).orElseThrow();
        assertEquals(2, c.getIssueCount());
        assertEquals(0, c.getFailedAttempts());
        if (!old.equals(replacement)) call("POST", path(id) + "/verify", agent, Map.of("code", old)).andExpect(status().isBadRequest());
        c = confirmations.findById(id).orElseThrow();
        c.setIssuedAt(Instant.now().minusSeconds(61));
        c.setIssueCount(5);
        confirmations.saveAndFlush(c);
        call("POST", path(id) + "/code", sender, null).andExpect(status().isTooManyRequests());
        c.setIssueWindowAt(Instant.now().minusSeconds(3601));
        confirmations.saveAndFlush(c);
        issue(id);
        assertEquals(1, confirmations.findById(id).orElseThrow().getIssueCount());
    }

    @Test
    void stateAndInputGuardsCannotBeBypassedAndLegacyWorkflowStaysAvailable() throws Exception {
        UUID id = booking(false);
        for (Object body : List.of(Map.of(), Map.of("code", "123"), Map.of("code", "abcdef"), Map.of("code", "1234567"))) {
            call("POST", path(id) + "/verify", agent, body).andExpect(status().isBadRequest());
        }
        for (DeliveryStatus state : List.of(DeliveryStatus.MATCHED, DeliveryStatus.IN_TRANSIT, DeliveryStatus.DISPUTED, DeliveryStatus.CANCELLED, DeliveryStatus.COMPLETED)) {
            state(id, state, EscrowStatus.DEMO_HELD);
            call("POST", path(id) + "/code", sender, null).andExpect(status().isBadRequest());
            call("POST", path(id) + "/verify", agent, Map.of("code", "123456")).andExpect(status().isBadRequest());
        }
        state(id, DeliveryStatus.ARRIVED, EscrowStatus.NONE);
        call("POST", path(id) + "/code", sender, null).andExpect(status().isBadRequest());
        state(id, DeliveryStatus.ARRIVED, EscrowStatus.DEMO_HELD);
        var p = packages.findById(id).orElseThrow();
        p.setDeliveryCodeRequired(false);
        packages.saveAndFlush(p);
        assertFalse(ok("GET", path(id), sender, null).get("required").asBoolean());
        call("POST", path(id) + "/code", sender, null).andExpect(status().isBadRequest());
        ok("PUT", root(id) + "/status", agent, Map.of("status", "DELIVERED"));
        ok("PUT", root(id) + "/status", sender, Map.of("status", "COMPLETED"));
    }

    @Test
    void concurrentVerificationCompletesExactlyOnce() throws Exception {
        UUID id = booking(false);
        String code = issue(id);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> attempts = new ArrayList<>();
            for (String token : List.of(agent, admin)) attempts.add(pool.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return call("POST", path(id) + "/verify", token, Map.of("code", code)).andReturn().getResponse().getStatus();
            }));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var attempt : attempts) statuses.add(attempt.get(20, TimeUnit.SECONDS));
            Collections.sort(statuses);
            assertEquals(List.of(200, 400), statuses);
        }
        assertEquals(2, events.countByRequestId(id));
        assertEquals(DeliveryStatus.COMPLETED, packages.findById(id).orElseThrow().getStatus());
    }
}
