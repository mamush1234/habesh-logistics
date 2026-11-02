package com.habeshlist;

import com.fasterxml.jackson.databind.*;
import com.habeshlist.entity.enums.MarketplaceTypes.*;
import com.habeshlist.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarketplaceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired DeliveryRequestRepository requests;
    @Autowired MarketTripRepository trips;
    @Autowired UserRepository users;
    @Autowired DeliveryMessageRepository chatMessages;
    @Autowired DeliveryOfferRepository deliveryOffers;
    @Autowired PackageNotificationRepository packageNotifications;
    @Autowired ShipmentRequestRepository shipmentRequests;
    @Autowired com.habeshlist.admin.PlatformSettingsRepository platformSettings;
    @Autowired com.habeshlist.service.ShipmentRequestExpiryJob shipmentExpiry;
    @Autowired com.habeshlist.service.DataSeeder seeder;
    private final String origin = "Origin-" + UUID.randomUUID();
    private final String destination = "Destination-" + UUID.randomUUID();
    private final String base = "/api/marketplace";
    @Test
    void accountOnlyDemoDoesNotReseedOperationalRecordsForExistingUsers() {
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "demoOperationalDataEnabled", false);
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "demoMarketplaceTripsEnabled", true);
        try {
            long before = trips.count();
            long requestsBefore = requests.count();
            long usersBefore = users.count();
            seeder.seed();
            assertEquals(before, trips.count());
            assertEquals(requestsBefore, requests.count());
            assertEquals(usersBefore, users.count());
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(seeder, "demoOperationalDataEnabled", true);
            org.springframework.test.util.ReflectionTestUtils.setField(seeder, "demoMarketplaceTripsEnabled", false);
        }
    }
    @Test
    void demoTripsSeedForExistingAccountsWithoutDuplicatesAndSupportSenderMatches() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "demoMarketplaceTripsEnabled", true);
        try {
            long before = trips.count();
            seeder.seed();
            assertEquals(before + 9, trips.count());
            seeder.seed();
            assertEquals(before + 9, trips.count());
            String s = sender();
            var input = requestInput();
            input.put("pickupCity", "Washington, DC");
            input.put("destinationCity", "Addis Ababa");
            input.put("availableFrom", date(0));
            input.put("deadline", date(15));
            JsonNode r = ok("POST", base + "/requests", s, input);
            assertEquals(6, ok("GET", path(r) + "/matches", s, null).size());
            input.put("itemType", "MEDICINE");
            JsonNode medicine = ok("POST", base + "/requests", s, input);
            assertEquals(0, ok("GET", path(medicine) + "/matches", s, null).size());
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(seeder, "demoMarketplaceTripsEnabled", false);
        }
    }
    private String date(int days) { return LocalDate.now().plusDays(days).toString(); }
    private ResultActions call(String method, String path, String token, Object body) throws Exception {
        var request = switch (method) {
            case "GET" -> get(path);
            case "PUT" -> put(path);
            default -> post(path);
        };
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return mvc.perform(request);
    }
    private JsonNode ok(String method, String path, String token, Object body) throws Exception {
        return om.readTree(call(method, path, token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private String login(String email, String password) throws Exception {
        return ok("POST", "/api/auth/login", null, Map.of("email", email, "password", password)).get("token").asText();
    }
    private String sender() throws Exception { return login("sender@example.com", "Sender@123"); }
    private String agentCarrier() throws Exception { return login("agent@example.com", "Agent@123"); }
    private String admin() throws Exception { return login("admin@habeshlist.com", "Admin@123"); }
    private String newAgentCarrier() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        ok("POST", "/api/admin/users", admin(), Map.of("email", email,
                "password", "New-agent-password-2026", "firstName", com.habeshlist.util.CodeGenerator.randomToken(16),
                "lastName", "Agent", "role", "AGENT"));
        return login(email, "New-agent-password-2026");
    }
    private Map<String, Object> tripInput() {
        return new HashMap<>(Map.of("origin", origin, "destination", destination, "departureDate", date(3),
                "expectedArrivalDate", date(4), "capacityKg", 5, "allowedItems", List.of("CLOTHING", "DOCUMENTS"),
                "blockedItems", List.of("FOOD", "MEDICINE")));
    }
    private JsonNode trip(String token) throws Exception { return ok("POST", base + "/trips", token, tripInput()); }
    private Map<String, Object> requestInput() {
        return new HashMap<>(Map.of("pickupCity", origin, "destinationCity", destination, "availableFrom", date(1),
                "deadline", date(5), "itemType", "CLOTHING", "description", "Sealed clothing parcel",
                "weightKg", 4, "offeredPrice", 50));
    }
    private JsonNode request(String token) throws Exception { return ok("POST", base + "/requests", token, requestInput()); }
    private JsonNode pricedTrip(String token) throws Exception {
        if (!platformSettings.existsById(com.habeshlist.admin.PlatformSettings.SINGLETON_ID))
            platformSettings.save(com.habeshlist.admin.PlatformSettings.builder()
                    .id(com.habeshlist.admin.PlatformSettings.SINGLETON_ID)
                    .platformFeePercent(new java.math.BigDecimal("10.00"))
                    .agentCommissionPercent(new java.math.BigDecimal("10.00"))
                    .feePayer(com.habeshlist.admin.PlatformSettings.FeePayer.SENDER)
                    .updatedAt(java.time.Instant.now()).build());
        var input = tripInput();
        input.put("itemPrices", List.of(
                Map.of("category", "CLOTHING", "price", 3.25, "unit", "PER_KG"),
                Map.of("category", "DOCUMENTS", "price", 10, "unit", "PER_ITEM")));
        return ok("POST", base + "/trips", token, input);
    }
    private String quotePath(JsonNode p, JsonNode t) {
        return base + "/shipment-requests/quote?packageId=" + p.get("id").asText() + "&tripId=" + t.get("id").asText();
    }
    private Map<String, Object> shipmentInput(JsonNode quote) {
        return new HashMap<>(Map.of("packageId", quote.get("packageId").asText(), "tripId", quote.get("tripId").asText(),
                "quoteKey", quote.get("quoteKey").asText(), "message", "Please review this package"));
    }

    private JsonNode targeted(String sender, String carrier) throws Exception {
        JsonNode p = request(sender), trip = pricedTrip(carrier);
        return ok("POST", base + "/shipment-requests", sender,
                shipmentInput(ok("GET", quotePath(p, trip), sender, null)));
    }

    private String decisionPath(JsonNode r) {
        return base + "/shipment-requests/" + r.get("id").asText() + "/decision";
    }

    private Map<String, Object> decision(JsonNode r, String action) {
        return new HashMap<>(Map.of("action", action, "quoteKey", r.get("quote").get("quoteKey").asText()));
    }

    @Test
    void targetedAgentAcceptanceReservesOnceScopesInboxAndBlocksDemoFunding() throws Exception {
        String s = sender(), t = agentCarrier(), other = newAgentCarrier(), a = admin();
        JsonNode r = targeted(s, t);
        assertFalse(r.get("canReview").asBoolean());
        JsonNode inbox = ok("GET", base + "/shipment-requests/inbox", t, null);
        JsonNode own = null;
        for (JsonNode row : inbox) if (row.get("id").equals(r.get("id"))) own = row;
        assertNotNull(own);
        assertTrue(own.get("canReview").asBoolean());
        assertFalse(ok("GET", base + "/shipment-requests/inbox", other, null).findValues("id").contains(r.get("id")));
        assertTrue(ok("GET", base + "/shipment-requests/inbox", a, null).findValues("id").contains(r.get("id")));
        call("POST", decisionPath(r), s, decision(r, "ACCEPT")).andExpect(status().isForbidden());
        call("POST", decisionPath(r), other, decision(r, "ACCEPT")).andExpect(status().isForbidden());
        JsonNode accepted = ok("POST", decisionPath(r), t, decision(r, "ACCEPT"));
        assertEquals("ACCEPTED", accepted.get("status").asText());
        assertNotNull(accepted.get("decidedAt"));
        UUID packageId = UUID.fromString(r.get("packageId").asText());
        UUID tripId = UUID.fromString(r.get("tripId").asText());
        JsonNode p = ok("GET", base + "/requests/" + packageId, s, null);
        assertEquals("MATCHED", p.get("status").asText());
        assertTrue(p.get("targetedShipment").asBoolean());
        assertTrue(p.get("travelerContact").isNull());
        assertEquals("NONE", p.get("escrowStatus").asText());
        assertEquals(4, trips.findById(tripId).orElseThrow().getReservedKg().intValueExact());
        assertNull(shipmentRequests.findById(UUID.fromString(r.get("id").asText())).orElseThrow().getPendingPackageId());
        call("POST", decisionPath(r), t, decision(r, "ACCEPT")).andExpect(status().isBadRequest());
        assertEquals(4, trips.findById(tripId).orElseThrow().getReservedKg().intValueExact());
        call("POST", base + "/requests/" + packageId + "/demo-escrow", s, null).andExpect(status().isBadRequest());
        ok("PUT", base + "/requests/" + packageId + "/status", s, Map.of("status", "CANCELLED"));
        assertEquals(0, trips.findById(tripId).orElseThrow().getReservedKg().signum());
    }

    @Test
    void targetedTravelerRequestsCanOnlyBeManagedByAdminWithoutReenablingTravelerActions() throws Exception {
        String s = sender(), t = login("traveler@example.com", "Traveler@123"), agent = agentCarrier(), a = admin();
        JsonNode r = targeted(s, t);
        call("GET", base + "/shipment-requests/inbox", t, null).andExpect(status().isForbidden());
        for (String action : List.of("ACCEPT", "DECLINE", "PROPOSE_PRICE")) {
            var input = decision(r, action);
            input.put("proposedPrice", 20);
            call("POST", decisionPath(r), t, input).andExpect(status().isForbidden());
            call("POST", decisionPath(r), agent, input).andExpect(status().isForbidden());
        }
        JsonNode accepted = ok("POST", decisionPath(r), a, decision(r, "ACCEPT"));
        assertEquals("ACCEPTED", accepted.get("status").asText());
        JsonNode p = ok("GET", base + "/requests/" + r.get("packageId").asText(), s, null);
        assertEquals("MATCHED", p.get("status").asText());
        assertTrue(p.get("targetedShipment").asBoolean());
        assertTrue(p.get("travelerContact").isNull());
        call("POST", base + "/requests/" + r.get("packageId").asText() + "/demo-escrow", t, null)
                .andExpect(status().isForbidden());
        call("PUT", base + "/requests/" + r.get("packageId").asText() + "/status", t, Map.of("status", "PICKED_UP"))
                .andExpect(status().isForbidden());
        assertEquals(4, trips.findById(UUID.fromString(r.get("tripId").asText())).orElseThrow().getReservedKg().intValueExact());
    }

    @Test
    void targetedPriceProposalsRequireLatestSenderConsentAndKeepOriginalExpiryAndFeeSnapshot() throws Exception {
        String s = sender(), t = agentCarrier(), a = admin();
        JsonNode r = targeted(s, t);
        JsonNode original = r.get("quote").deepCopy();
        var propose = decision(r, "PROPOSE_PRICE");
        propose.put("proposedPrice", 20);
        JsonNode revised = ok("POST", decisionPath(r), t, propose);
        assertEquals("PENDING", revised.get("status").asText());
        assertEquals(r.get("expiresAt"), revised.get("expiresAt"));
        assertEquals(original, revised.get("quote"));
        assertEquals(20, revised.get("proposedQuote").get("packageAmount").asDouble());
        assertEquals(22, revised.get("proposedQuote").get("total").asDouble());
        assertEquals(0, trips.findById(UUID.fromString(r.get("tripId").asText())).orElseThrow().getReservedKg().signum());
        call("POST", decisionPath(r), t, decision(r, "ACCEPT")).andExpect(status().isBadRequest());
        var confirm = new HashMap<String, Object>(Map.of("action", "CONFIRM_PRICE",
                "quoteKey", revised.get("proposedQuote").get("quoteKey").asText()));
        call("POST", decisionPath(r), t, confirm).andExpect(status().isForbidden());
        call("POST", decisionPath(r), a, confirm).andExpect(status().isForbidden());
        propose.put("proposedPrice", 25);
        JsonNode latest = ok("POST", decisionPath(r), t, propose);
        call("POST", decisionPath(r), s, confirm).andExpect(status().isBadRequest());
        platformSettings.findById(com.habeshlist.admin.PlatformSettings.SINGLETON_ID).orElseThrow()
                .setPlatformFeePercent(new java.math.BigDecimal("50"));
        confirm.put("quoteKey", latest.get("proposedQuote").get("quoteKey").asText());
        JsonNode accepted = ok("POST", decisionPath(r), s, confirm);
        assertEquals("ACCEPTED", accepted.get("status").asText());
        assertEquals(27.5, accepted.get("proposedQuote").get("total").asDouble());
        assertEquals(10, accepted.get("proposedQuote").get("platformFeePercent").asDouble());
        assertEquals(original, accepted.get("quote"));
        var p = packagesForTargeted(r);
        assertEquals(25, p.getAcceptedOffer().getPrice().doubleValue());
        assertEquals("MATCHED", p.getStatus().name());
        assertEquals(4, trips.findById(UUID.fromString(r.get("tripId").asText())).orElseThrow().getReservedKg().intValueExact());
    }

    private com.habeshlist.entity.DeliveryRequest packagesForTargeted(JsonNode r) {
        return requests.findById(UUID.fromString(r.get("packageId").asText())).orElseThrow();
    }

    @Test
    void targetedDeclineRejectAndExpiredDecisionReleasePendingSlotWithoutReservingCapacity() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode r = targeted(s, t);
        JsonNode declined = ok("POST", decisionPath(r), t, decision(r, "DECLINE"));
        assertEquals("DECLINED", declined.get("status").asText());
        assertEquals("REQUESTED", packagesForTargeted(r).getStatus().name());
        var input = new HashMap<String, Object>(Map.of("packageId", r.get("packageId").asText(), "tripId", r.get("tripId").asText(),
                "quoteKey", r.get("quote").get("quoteKey").asText()));
        JsonNode second = ok("POST", base + "/shipment-requests", s, input);
        var proposal = decision(second, "PROPOSE_PRICE"); proposal.put("proposedPrice", 30);
        JsonNode proposed = ok("POST", decisionPath(second), t, proposal);
        JsonNode rejected = ok("POST", decisionPath(second), s, Map.of("action", "REJECT_PRICE",
                "quoteKey", proposed.get("proposedQuote").get("quoteKey").asText()));
        assertEquals("DECLINED", rejected.get("status").asText());
        JsonNode third = ok("POST", base + "/shipment-requests", s, input);
        var stored = shipmentRequests.findById(UUID.fromString(third.get("id").asText())).orElseThrow();
        stored.setCreatedAt(java.time.Instant.now().minusSeconds(7200));
        stored.setExpiresAt(java.time.Instant.now().minusSeconds(3600));
        JsonNode expired = ok("POST", decisionPath(third), t, decision(third, "ACCEPT"));
        assertEquals("EXPIRED", expired.get("status").asText());
        assertNull(stored.getPendingPackageId());
        assertEquals("REQUESTED", packagesForTargeted(r).getStatus().name());
        assertEquals(0, trips.findById(UUID.fromString(r.get("tripId").asText())).orElseThrow().getReservedKg().signum());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTargetedAcceptancesCannotOverbookSharedTrip() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode trip = pricedTrip(t), p1 = request(s), p2 = request(s);
        JsonNode r1 = ok("POST", base + "/shipment-requests", s, shipmentInput(ok("GET", quotePath(p1, trip), s, null)));
        JsonNode r2 = ok("POST", base + "/shipment-requests", s, shipmentInput(ok("GET", quotePath(p2, trip), s, null)));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (JsonNode r : List.of(r1, r2)) results.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return call("POST", decisionPath(r), t, decision(r, "ACCEPT")).andReturn().getResponse().getStatus();
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(code -> code == 200).count());
            assertEquals(1, statuses.stream().filter(code -> code == 400).count());
        }
        assertEquals(4, trips.findById(UUID.fromString(trip.get("id").asText())).orElseThrow().getReservedKg().intValueExact());
    }

    @Test
    void targetedShipmentRequestPricesEveryItemUsesSettingsAndPreservesLegacyOffers() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode trip = pricedTrip(t);
        var input = requestInput();
        input.put("items", List.of(
                Map.of("category", "CLOTHING", "weightKg", 1.15, "description", "Clothes one"),
                Map.of("category", "CLOTHING", "weightKg", 1.15, "description", "Clothes two"),
                Map.of("category", "DOCUMENTS", "weightKg", 0.1, "description", "Passport"),
                Map.of("category", "DOCUMENTS", "weightKg", 0.1, "description", "Paperwork")));
        input.put("receiver", Map.of("name", "Receiver", "phone", "+251911123456", "address", "Addis Ababa"));
        JsonNode p = ok("POST", base + "/requests", s, input);
        var settings = platformSettings.findById(com.habeshlist.admin.PlatformSettings.SINGLETON_ID).orElseThrow();
        settings.setPlatformFeePercent(new java.math.BigDecimal("7.50"));
        settings.setFeePayer(com.habeshlist.admin.PlatformSettings.FeePayer.SENDER);
        JsonNode quote = ok("GET", quotePath(p, trip), s, null);
        assertEquals(27.48, quote.get("packageAmount").asDouble());
        assertEquals(4, quote.get("items").size());
        assertEquals(3.74, quote.get("items").get(0).get("amount").asDouble());
        assertEquals(10, quote.get("items").get(2).get("amount").asDouble());
        assertEquals(2.06, quote.get("platformFee").asDouble());
        assertEquals(29.54, quote.get("total").asDouble());
        settings.setFeePayer(com.habeshlist.admin.PlatformSettings.FeePayer.TRAVELER);
        JsonNode carrierPaid = ok("GET", quotePath(p, trip), s, null);
        assertEquals(27.48, carrierPaid.get("total").asDouble());
        assertEquals(25.42, carrierPaid.get("travelerPayout").asDouble());
        settings.setFeePayer(com.habeshlist.admin.PlatformSettings.FeePayer.SPLIT);
        settings.setPlatformFeePercent(new java.math.BigDecimal("0.04"));
        JsonNode split = ok("GET", quotePath(p, trip), s, null);
        assertEquals(0.01, split.get("platformFee").asDouble());
        assertEquals(0.01, split.get("travelerFee").asDouble());
        assertEquals(0, split.get("senderFee").asDouble());
        call("POST", base + "/shipment-requests", s, shipmentInput(quote)).andExpect(status().isBadRequest());
        JsonNode sent = ok("POST", base + "/shipment-requests", s, shipmentInput(split));
        assertEquals("PENDING", sent.get("status").asText());
        assertEquals(split, sent.get("quote"));
        assertFalse(sent.get("adminManaged").asBoolean());
        assertEquals(java.time.Duration.ofHours(1), java.time.Duration.between(
                java.time.Instant.parse(sent.get("createdAt").asText()), java.time.Instant.parse(sent.get("expiresAt").asText())));
        assertEquals(sent.get("id"), ok("POST", base + "/shipment-requests", s, shipmentInput(split)).get("id"));
        assertEquals(1, ok("GET", base + "/shipment-requests?packageId=" + p.get("id").asText(), t, null).size());
        var storedTrip = trips.findById(UUID.fromString(trip.get("id").asText())).orElseThrow();
        assertEquals(0, storedTrip.getReservedKg().signum());
        JsonNode unchanged = ok("GET", path(p), s, null);
        assertEquals("REQUESTED", unchanged.get("status").asText());
        assertTrue(unchanged.get("acceptedOfferId").isNull());
        assertEquals("NONE", unchanged.get("escrowStatus").asText());
        call("POST", path(p) + "/demo-escrow", s, null).andExpect(status().isBadRequest());
        JsonNode offer = offer(t, p, trip, 40);
        ok("POST", path(p) + "/offers/" + offer.get("id").asText() + "/accept", s, null);
        JsonNode history = ok("GET", base + "/shipment-requests?packageId=" + p.get("id").asText(), s, null);
        assertEquals("EXPIRED", history.get(0).get("status").asText());
        assertEquals(split, history.get(0).get("quote"));
    }

    @Test
    void targetedRequestsScopeOwnersRouteTravelerTripsToAdminAndExpireWithoutReservingSpace() throws Exception {
        String s = sender(), a = admin(), t = agentCarrier(), otherAgent = newAgentCarrier();
        String traveler = login("traveler@example.com", "Traveler@123");
        JsonNode trip = pricedTrip(traveler), p = request(s);
        JsonNode quote = ok("GET", quotePath(p, trip), s, null);
        for (String token : List.of(t, traveler, a))
            call("POST", base + "/shipment-requests", token, shipmentInput(quote)).andExpect(status().isForbidden());
        call("GET", quotePath(p, trip), null, null).andExpect(status().isUnauthorized());
        JsonNode sent = ok("POST", base + "/shipment-requests", s, shipmentInput(quote));
        assertTrue(sent.get("adminManaged").asBoolean());
        String list = base + "/shipment-requests?packageId=" + p.get("id").asText();
        assertEquals(1, ok("GET", list, a, null).size());
        assertEquals(0, ok("GET", list, otherAgent, null).size());
        call("GET", list, traveler, null).andExpect(status().isForbidden());
        assertTrue(ok("GET", "/api/package-queue/notifications", a, null).toString().contains("Admin review is required"));
        assertFalse(ok("GET", "/api/package-queue/notifications", traveler, null).toString().contains("targeted shipment request"));
        JsonNode otherTrip = pricedTrip(t);
        JsonNode otherQuote = ok("GET", quotePath(p, otherTrip), s, null);
        call("POST", base + "/shipment-requests", s, shipmentInput(otherQuote)).andExpect(status().isBadRequest());
        var stored = shipmentRequests.findById(UUID.fromString(sent.get("id").asText())).orElseThrow();
        java.time.Instant boundary = stored.getExpiresAt();
        shipmentRequests.expirePending(boundary.minusMillis(1), LocalDate.now());
        assertEquals("PENDING", shipmentRequests.findById(stored.getId()).orElseThrow().getStatus().name());
        shipmentRequests.expirePending(boundary, LocalDate.now());
        assertEquals("EXPIRED", shipmentRequests.findById(stored.getId()).orElseThrow().getStatus().name());
        assertNull(shipmentRequests.findById(stored.getId()).orElseThrow().getPendingPackageId());
        // A fresh quote after expiry can target a different trip, without reusing an old pending slot.
        JsonNode freshQuote = ok("GET", quotePath(p, otherTrip), s, null);
        JsonNode replacement = ok("POST", base + "/shipment-requests", s, shipmentInput(freshQuote));
        assertNotEquals(sent.get("id"), replacement.get("id"));
        var replacementRow = shipmentRequests.findById(UUID.fromString(replacement.get("id").asText())).orElseThrow();
        replacementRow.setCreatedAt(java.time.Instant.now().minusSeconds(7200));
        replacementRow.setExpiresAt(java.time.Instant.now().minusSeconds(3600));
        shipmentRequests.flush();
        shipmentExpiry.expire();
        assertEquals("EXPIRED", shipmentRequests.findById(replacementRow.getId()).orElseThrow().getStatus().name());
        assertEquals(0, trips.findById(UUID.fromString(trip.get("id").asText())).orElseThrow().getReservedKg().signum());
    }

    @Test
    void targetedQuotesRejectPrivateIncompatibleUnpricedAndChangedPackages() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode trip = pricedTrip(t), p = request(s);
        JsonNode otherSender = ok("POST", "/api/auth/register", null, Map.of("email", UUID.randomUUID() + "@example.com",
                "password", "Sender-password-2026", "firstName", "Other", "lastName", "Sender", "role", "SENDER"));
        String other = otherSender.get("token").asText();
        call("GET", quotePath(p, trip), other, null).andExpect(status().isForbidden());
        JsonNode quote = ok("GET", quotePath(p, trip), s, null);
        call("POST", base + "/shipment-requests", other, shipmentInput(quote)).andExpect(status().isForbidden());
        call("GET", base + "/shipment-requests?packageId=" + p.get("id").asText(), other, null).andExpect(status().isForbidden());
        JsonNode legacy = trip(t);
        call("GET", quotePath(p, legacy), s, null).andExpect(status().isBadRequest());
        var storedTrip = trips.findById(UUID.fromString(trip.get("id").asText())).orElseThrow();
        storedTrip.getItemPrices().stream().filter(price -> price.getCategory() == ItemType.CLOTHING)
                .findFirst().orElseThrow().setPrice(new java.math.BigDecimal("9.00"));
        call("POST", base + "/shipment-requests", s, shipmentInput(quote)).andExpect(status().isBadRequest());
        storedTrip.setReservedKg(storedTrip.getCapacityKg());
        call("GET", quotePath(p, trip), s, null).andExpect(status().isBadRequest());
        storedTrip.setReservedKg(java.math.BigDecimal.ZERO);
        storedTrip.setDestination("Not the package destination");
        call("GET", quotePath(p, trip), s, null).andExpect(status().isBadRequest());
        storedTrip.setDestination(destination);
        storedTrip.setExpectedArrivalDate(LocalDate.now().plusDays(20));
        call("GET", quotePath(p, trip), s, null).andExpect(status().isBadRequest());
        storedTrip.setExpectedArrivalDate(LocalDate.now().plusDays(4));
        storedTrip.getBlockedItems().add(com.habeshlist.entity.enums.MarketplaceTypes.ItemType.CLOTHING);
        call("GET", quotePath(p, trip), s, null).andExpect(status().isBadRequest());
        users.findByEmailIgnoreCase("sender@example.com").orElseThrow().setRole(com.habeshlist.entity.enums.Role.TRAVELER);
        String formerSender = login("sender@example.com", "Sender@123");
        call("GET", base + "/shipment-requests?packageId=" + p.get("id").asText(), formerSender, null)
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTargetedRequestRetriesCreateOnePendingRowAndOneNotification() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode p = request(s), trip = pricedTrip(t);
        Map<String, Object> input = shipmentInput(ok("GET", quotePath(p, trip), s, null));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<JsonNode>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return ok("POST", base + "/shipment-requests", s, input);
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(results.get(0).get(20, TimeUnit.SECONDS).get("id"), results.get(1).get(20, TimeUnit.SECONDS).get("id"));
        }
        assertEquals(1, shipmentRequests.findByPackageRequestIdOrderByCreatedAtDesc(UUID.fromString(p.get("id").asText())).size());
        int notices = 0;
        for (JsonNode notice : ok("GET", "/api/package-queue/notifications", s, null))
            if (notice.get("requestId").equals(p.get("id")) && notice.get("message").asText()
                    .equals("Shipment request sent. Check the package for its status. It expires in one hour.")) notices++;
        assertEquals(1, notices);
        assertEquals(0, trips.findById(UUID.fromString(trip.get("id").asText())).orElseThrow().getReservedKg().signum());
    }
    @Test
    void signedInTripPersistsPricesAndOptionalDetailsWhileLegacyTripsRemainUnpriced() throws Exception {
        String carrier = agentCarrier();
        JsonNode legacy = trip(carrier);
        assertTrue(legacy.get("itemPrices").isEmpty());
        assertTrue(legacy.get("handoffLocation").isNull());
        var input = tripInput();
        input.put("handoffLocation", " Airport public meeting point ");
        input.put("notes", " Carry-on items only ");
        input.put("itemPrices", List.of(
                Map.of("category", "CLOTHING", "price", 0.01, "unit", "PER_KG"),
                Map.of("category", "DOCUMENTS", "price", 99999999.99, "unit", "PER_ITEM")));
        JsonNode saved = ok("POST", base + "/trips", carrier, input);
        assertEquals("Airport public meeting point", saved.get("handoffLocation").asText());
        assertEquals("Carry-on items only", saved.get("notes").asText());
        JsonNode reloaded = null;
        for (JsonNode row : ok("GET", base + "/trips", carrier, null))
            if (row.get("id").equals(saved.get("id"))) reloaded = row;
        assertNotNull(reloaded);
        assertEquals(saved.get("itemPrices"), reloaded.get("itemPrices"));
        assertEquals("DOCUMENTS", reloaded.get("itemPrices").get(0).get("category").asText());
        assertEquals(99999999.99, reloaded.get("itemPrices").get(0).get("price").asDouble());
        assertEquals("CLOTHING", reloaded.get("itemPrices").get(1).get("category").asText());
        assertEquals(0.01, reloaded.get("itemPrices").get(1).get("price").asDouble());
        input.put("itemPrices", List.of(Map.of("category", "CLOTHING", "price", 5, "unit", "PER_KG")));
        long before = trips.count();
        call("POST", base + "/trips", carrier, input).andExpect(status().isBadRequest());
        assertEquals(before, trips.count());
    }
    @Test
    void matchesAllItemCategoriesReservesTotalWeightAndKeepsReceiverPrivateUntilFunding() throws Exception {
        String s = sender(), carrier = agentCarrier(), other = newAgentCarrier(), a = admin();
        var input = requestInput();
        input.put("items", List.of(Map.of("category", "CLOTHING", "weightKg", 2, "description", "Shirts"),
                Map.of("category", "LAPTOP", "weightKg", 2.5, "description", "Laptop")));
        input.put("receiver", Map.of("name", "Private receiver", "phone", "+251911234567", "address", "Bole, Addis Ababa"));
        input.put("weightKg", 0.1);
        JsonNode r = ok("POST", base + "/requests", s, input);
        assertEquals(4.5, r.get("weightKg").asDouble());
        JsonNode unsupported = trip(carrier);
        call("POST", path(r) + "/offers", carrier, Map.of("tripId", unsupported.get("id").asText(), "price", 40))
                .andExpect(status().isBadRequest());
        assertEquals(0, ok("GET", path(r) + "/matches", s, null).size());
        var tripInput = tripInput();
        tripInput.put("allowedItems", List.of("CLOTHING", "LAPTOP"));
        JsonNode supported = ok("POST", base + "/trips", carrier, tripInput);
        assertEquals(1, ok("GET", path(r) + "/matches", s, null).size());
        assertTrue(ok("GET", path(r), carrier, null).get("receiver").isNull());
        assertEquals("Private receiver", ok("GET", path(r), a, null).get("receiver").get("name").asText());
        ok("PUT", path(r) + "/approval", a, Map.of("approved", true));
        assertTrue(ok("GET", path(r), login("traveler@example.com", "Traveler@123"), null).get("receiver").isNull());
        assertFalse(ok("GET", "/api/public/requests", null, null).toString().contains("Private receiver"));
        accept(s, r, offer(carrier, r, supported, 40));
        var reservedTrip = trips.findById(UUID.fromString(supported.get("id").asText())).orElseThrow();
        assertEquals(4.5, reservedTrip.getReservedKg().doubleValue());
        assertEquals(0.5, reservedTrip.getCapacityKg().subtract(reservedTrip.getReservedKg()).doubleValue());
        assertTrue(ok("GET", path(r), carrier, null).get("receiver").isNull());
        ok("POST", path(r) + "/demo-escrow", s, null);
        assertEquals("Private receiver", ok("GET", path(r), carrier, null).get("receiver").get("name").asText());
        call("GET", path(r), other, null).andExpect(status().isForbidden());
        assertTrue(ok("GET", path(r), login("traveler@example.com", "Traveler@123"), null).get("receiver").isNull());
    }
    private String path(JsonNode request) { return base + "/requests/" + request.get("id").asText(); }
    private JsonNode offer(String token, JsonNode request, JsonNode trip, int price) throws Exception {
        JsonNode result = ok("POST", path(request) + "/offers", token, Map.of("tripId", trip.get("id").asText(), "price", price));
        return result.get("offers").get(0);
    }
    private JsonNode accept(String token, JsonNode request, JsonNode offer) throws Exception {
        return ok("POST", path(request) + "/offers/" + offer.get("id").asText() + "/accept", token, null);
    }
    private JsonNode move(String token, JsonNode r, String status) throws Exception {
        return ok("PUT", path(r) + "/status", token, Map.of("status", status));
    }
    private JsonNode confirmDelivery(String sender, String verifier, JsonNode request) throws Exception {
        String code = ok("POST", path(request) + "/delivery-confirmation/code", sender, null).get("code").asText();
        ok("POST", path(request) + "/delivery-confirmation/verify", verifier, Map.of("code", code));
        return ok("GET", path(request), sender, null);
    }
    private JsonNode matched(String sender, String traveler, JsonNode trip) throws Exception {
        JsonNode r = request(sender);
        return accept(sender, r, offer(traveler, r, trip, 40));
    }

    @Test
    void onlyAdminsApproveAndTravelersCannotBypassVisibilityThroughOtherReadPaths() throws Exception {
        String s = sender(), a = admin(), agent = agentCarrier(), t = login("traveler@example.com", "Traveler@123");
        JsonNode r = request(s), travelerTrip = trip(t);
        String path = path(r), id = r.path("id").asText(), approval = path + "/approval";
        var stored = requests.findById(UUID.fromString(id)).orElseThrow();
        stored.setProductPhoto("data:image/jpeg;base64,dGVzdA==");
        assertFalse(r.path("adminApproved").asBoolean());
        assertTrue(ok("GET", base + "/requests", a, null).findValues("id").contains(r.path("id")));
        assertTrue(ok("GET", base + "/requests", s, null).findValues("id").contains(r.path("id")));
        assertTrue(ok("GET", base + "/requests", agent, null).findValues("id").contains(r.path("id")));
        assertFalse(ok("GET", base + "/requests", t, null).findValues("id").contains(r.path("id")));
        for (String readPath : List.of(path, path + "/product-photo", path + "/matches", path + "/messages"))
            call("GET", readPath, t, null).andExpect(status().isForbidden());
        assertFalse(ok("GET", base + "/trips/" + travelerTrip.path("id").asText() + "/matches", t, null)
                .toString().contains(id));
        assertFalse(ok("GET", "/api/public/requests", null, null).toString().contains(id));
        call("GET", "/api/public/requests/" + id + "/product-photo", null, null).andExpect(status().isNotFound());
        assertFalse(ok("GET", "/api/package-queue/notifications", t, null).toString().contains(id));
        call("PUT", approval, null, Map.of("approved", true)).andExpect(status().isUnauthorized());
        for (String token : List.of(s, agent, t))
            call("PUT", approval, token, Map.of("approved", true)).andExpect(status().isForbidden());
        call("PUT", approval, a, Map.of()).andExpect(status().isBadRequest());
        JsonNode approved = ok("PUT", approval, a, Map.of("approved", true));
        assertTrue(approved.path("adminApproved").asBoolean());
        int eventCount = approved.path("timeline").size();
        assertEquals(eventCount, ok("PUT", approval, a, Map.of("approved", true)).path("timeline").size());
        assertEquals(id, ok("GET", path, t, null).path("id").asText());
        call("GET", path + "/product-photo", t, null).andExpect(status().isOk());
        assertTrue(ok("GET", base + "/trips/" + travelerTrip.path("id").asText() + "/matches", t, null)
                .toString().contains(id));
        call("GET", path + "/matches", t, null).andExpect(status().isOk());
        assertTrue(ok("GET", "/api/public/requests", null, null).toString().contains(id));
        call("GET", "/api/public/requests/" + id + "/product-photo", null, null).andExpect(status().isOk());
        JsonNode notifications = ok("GET", "/api/package-queue/notifications", t, null);
        JsonNode notice = notifications.findParents("requestId").stream()
                .filter(n -> n.path("requestId").asText().equals(id)).findFirst().orElseThrow();
        assertTrue(notice.path("message").asText().contains("read-only"));
        ok("PUT", approval, a, Map.of("approved", false));
        assertFalse(ok("GET", base + "/requests", t, null).toString().contains(id));
        call("GET", path, t, null).andExpect(status().isForbidden());
        call("GET", path + "/product-photo", t, null).andExpect(status().isForbidden());
        assertFalse(ok("GET", "/api/public/requests", null, null).toString().contains(id));
        call("GET", "/api/public/requests/" + id + "/product-photo", null, null).andExpect(status().isNotFound());
        assertFalse(ok("GET", "/api/package-queue/notifications", t, null).toString().contains(id));
        call("PUT", "/api/package-queue/notifications/" + notice.path("id").asText() + "/read", t, null)
                .andExpect(status().isForbidden());
        call("GET", path, agent, null).andExpect(status().isOk());
        JsonNode agentTrip = trip(agent);
        assertTrue(offer(agent, r, agentTrip, 40).hasNonNull("id"));
    }

    @Test
    void travelersCannotMutateEvenPreviouslyMatchedApprovedRequestsAndAgentsStillCan() throws Exception {
        String s = sender(), a = admin(), t = login("traveler@example.com", "Traveler@123"), agent = agentCarrier();
        JsonNode r = request(s), travelerTrip = trip(t);
        String path = path(r);
        var stored = requests.findById(UUID.fromString(r.path("id").asText())).orElseThrow();
        var traveler = users.findByEmailIgnoreCase("traveler@example.com").orElseThrow();
        var oldOffer = deliveryOffers.saveAndFlush(com.habeshlist.entity.DeliveryOffer.builder().request(stored)
                .trip(trips.findById(UUID.fromString(travelerTrip.path("id").asText())).orElseThrow()).traveler(traveler)
                .price(new java.math.BigDecimal("40")).status(OfferStatus.PENDING).createdAt(java.time.Instant.now()).build());
        for (boolean approved : List.of(false, true)) {
            ok("PUT", path + "/approval", a, Map.of("approved", approved));
            call("POST", path + "/offers", t, Map.of("tripId", travelerTrip.path("id").asText(), "price", 40))
                    .andExpect(status().isForbidden());
            for (String action : List.of("accept", "decline", "withdraw"))
                call("POST", path + "/offers/" + oldOffer.getId() + "/" + action, t, null).andExpect(status().isForbidden());
            call("POST", path + "/demo-escrow", t, null).andExpect(status().isForbidden());
            call("POST", path + "/rating", t, Map.of("rating", 5)).andExpect(status().isForbidden());
            call("POST", path + "/messages", t, Map.of("body", "Disabled")).andExpect(status().isForbidden());
            for (String status : List.of("PICKED_UP", "IN_TRANSIT", "ARRIVED", "DELIVERED", "COMPLETED", "CANCELLED", "DISPUTED"))
                call("PUT", path + "/status", t, Map.of("status", status, "reason", "Disabled")).andExpect(status().isForbidden());
        }
        call("POST", path + "/offers/" + oldOffer.getId() + "/accept", s, null).andExpect(status().isBadRequest());
        oldOffer.setStatus(OfferStatus.ACCEPTED);
        stored.setAcceptedOffer(oldOffer);
        stored.setStatus(DeliveryStatus.PAID_ESCROW);
        stored.setEscrowStatus(EscrowStatus.DEMO_HELD);
        call("PUT", path + "/status", t, Map.of("status", "PICKED_UP")).andExpect(status().isForbidden());
        assertFalse(ok("GET", path + "/messages", t, null).path("writable").asBoolean());
        ok("POST", path + "/messages", s, Map.of("body", "Admin will coordinate"));
        call("POST", path + "/messages", t, Map.of("body", "Still disabled")).andExpect(status().isForbidden());
        ok("PUT", path + "/approval", a, Map.of("approved", false));
        call("GET", path, t, null).andExpect(status().isForbidden());
        call("GET", path + "/messages", t, null).andExpect(status().isForbidden());
        // Prior notification rows must not bypass approval after deployment.
        packageNotifications.saveAndFlush(com.habeshlist.entity.PackageNotification.builder().request(stored)
                .recipient(traveler).message("Legacy request notification").createdAt(java.time.Instant.now()).build());
        assertFalse(ok("GET", "/api/package-queue/notifications", t, null).toString().contains(r.path("id").asText()));
        assertEquals("PICKED_UP", move(a, r, "PICKED_UP").path("status").asText());
        JsonNode agentRequest = request(s), agentTrip = trip(agent);
        accept(s, agentRequest, offer(agent, agentRequest, agentTrip, 40));
        ok("POST", path(agentRequest) + "/demo-escrow", s, null);
        assertEquals("PICKED_UP", move(agent, agentRequest, "PICKED_UP").path("status").asText());
        ok("POST", path(agentRequest) + "/messages", agent, Map.of("body", "Agent can still coordinate"));
    }

    @Test
    void publicRequestPreviewsExposeOnlyOpenPackageFieldsAndGatePhotosByAvailability() throws Exception {
        String s = sender();
        JsonNode r = request(s);
        UUID id = UUID.fromString(r.path("id").asText());
        var stored = requests.findById(id).orElseThrow();
        stored.setProductPhoto("data:image/jpeg;base64,dGVzdA==");
        ok("PUT", path(r) + "/approval", admin(), Map.of("approved", true));
        String photoPath = "/api/public/requests/" + id + "/product-photo";
        JsonNode preview = ok("GET", "/api/public/requests", null, null).findParents("id").stream()
                .filter(row -> row.path("id").asText().equals(id.toString())).findFirst().orElseThrow();
        Set<String> fields = new HashSet<>();
        preview.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("id", "pickupCity", "destinationCity", "availableFrom", "deadline", "itemType",
                "weightKg", "offeredPrice", "hasProductPhoto", "createdAt"), fields);
        assertEquals(origin, preview.path("pickupCity").asText());
        assertTrue(preview.path("hasProductPhoto").asBoolean());
        call("GET", photoPath, null, null).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.photoDataUrl").value(stored.getProductPhoto()));
        for (String privatePath : List.of(path(r), path(r) + "/product-photo", path(r) + "/offers", path(r) + "/messages"))
            call("GET", privatePath, null, null).andExpect(status().isUnauthorized());
        for (DeliveryStatus status : DeliveryStatus.values()) {
            if (status == DeliveryStatus.REQUESTED) continue;
            stored.setStatus(status);
            assertFalse(ok("GET", "/api/public/requests", null, null).toString().contains(id.toString()));
            call("GET", photoPath, null, null).andExpect(status().isNotFound());
        }
        stored.setStatus(DeliveryStatus.REQUESTED);
        stored.setDeadline(LocalDate.now().minusDays(1));
        assertFalse(ok("GET", "/api/public/requests", null, null).toString().contains(id.toString()));
        call("GET", photoPath, null, null).andExpect(status().isNotFound());
        stored.setDeadline(LocalDate.now());
        assertTrue(ok("GET", "/api/public/requests", null, null).toString().contains(id.toString()));
        stored.getSender().setActive(false);
        assertFalse(ok("GET", "/api/public/requests", null, null).toString().contains(id.toString()));
        call("GET", photoPath, null, null).andExpect(status().isNotFound());
        stored.getSender().setActive(true);
        stored.setProductPhoto(null);
        call("GET", photoPath, null, null).andExpect(status().isNotFound());
        call("GET", "/api/public/requests/" + UUID.randomUUID() + "/product-photo", null, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void publicTripsAreNewestPostedFirstAndIncludeTripsBeyondOldSixItemPreview() throws Exception {
        String t = agentCarrier();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            var input = tripInput();
            input.put("departureDate", date(10 - i));
            input.put("expectedArrivalDate", date(11));
            JsonNode trip = ok("POST", base + "/trips", t, input);
            UUID id = UUID.fromString(trip.path("id").asText());
            ids.add(id);
            trips.findById(id).orElseThrow().setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
            assertTrue(trip.hasNonNull("createdAt"));
        }
        List<String> published = new ArrayList<>();
        for (JsonNode row : ok("GET", "/api/public/travel", null, null)) {
            if (ids.stream().anyMatch(id -> id.toString().equals(row.path("id").asText()))) {
                published.add(row.path("id").asText());
                Set<String> fields = new HashSet<>();
                row.fieldNames().forEachRemaining(fields::add);
                assertEquals(Set.of("id", "origin", "destination", "departureDate", "availableCapacityKg", "createdAt",
                        "expectedArrivalDate", "rating", "ratingCount", "verification", "allowedItems", "itemPrices"), fields);
            }
        }
        Collections.reverse(ids);
        assertEquals(ids.stream().map(UUID::toString).toList(), published);
        var latest = trips.findById(ids.get(0)).orElseThrow();
        latest.setReservedKg(latest.getCapacityKg());
        assertFalse(ok("GET", "/api/public/travel", null, null).toString().contains(latest.getId().toString()));
        latest.setReservedKg(java.math.BigDecimal.ZERO);
        latest.setDepartureDate(LocalDate.now().minusDays(1));
        assertFalse(ok("GET", "/api/public/travel", null, null).toString().contains(latest.getId().toString()));
        latest.setDepartureDate(LocalDate.now());
        assertTrue(ok("GET", "/api/public/travel", null, null).toString().contains(latest.getId().toString()));
        latest.getTraveler().setActive(false);
        assertFalse(ok("GET", "/api/public/travel", null, null).toString().contains(latest.getId().toString()));
    }

    @Test
    void agentNamesStayPrivateUntilAcceptanceAndContactsUntilFunding() throws Exception {
        String s = sender(), a = admin(), other = newAgentCarrier();
        for (String selected : List.of(agentCarrier(), newAgentCarrier())) {
            JsonNode trip = trip(selected), otherTrip = trip(other), r = request(s);
            String realName = trip.get("traveler").get("name").asText();
            String senderName = r.get("senderContact").get("name").asText();
            JsonNode before = ok("GET", path(r), selected, null);
            assertTrue(before.get("senderName").isNull());
            assertTrue(before.get("senderContact").isNull());
            JsonNode senderTrips = ok("GET", base + "/trips", s, null);
            assertFalse(senderTrips.toString().contains(realName));
            JsonNode matches = ok("GET", path(r) + "/matches", s, null);
            assertFalse(matches.toString().contains(realName));
            assertTrue(ok("GET", base + "/trips/" + trip.get("id").asText() + "/matches", selected, null)
                    .get(0).get("request").get("senderName").isNull());
            JsonNode selectedOffer = offer(selected, r, trip, 40);
            JsonNode otherOffer = offer(other, r, otherTrip, 45);
            JsonNode pending = ok("GET", path(r), s, null);
            for (JsonNode offer : pending.get("offers")) assertTrue(offer.get("traveler").get("name").asText().startsWith("Private "));
            assertTrue(ok("GET", path(r), a, null).toString().contains(realName));
            JsonNode matched = accept(s, r, selectedOffer);
            for (JsonNode offer : matched.get("offers")) {
                if (offer.get("id").equals(selectedOffer.get("id"))) assertEquals(realName, offer.get("traveler").get("name").asText());
                if (offer.get("id").equals(otherOffer.get("id"))) assertTrue(offer.get("traveler").get("name").asText().startsWith("Private "));
            }
            assertTrue(matched.get("travelerContact").isNull());
            JsonNode selectedView = ok("GET", path(r), selected, null);
            assertEquals(senderName, selectedView.get("senderName").asText());
            assertTrue(selectedView.get("senderContact").isNull());
            assertTrue(selectedView.get("travelerContact").isNull());
            ok("POST", path(r) + "/demo-escrow", s, null);
            assertEquals("sender@example.com", ok("GET", path(r), selected, null).get("senderContact").get("email").asText());
            assertFalse(ok("GET", path(r), s, null).get("travelerContact").isNull());
        }
    }

    @Test
    void packageChatUnlocksOnlyForSelectedParticipantsAndAdminsAfterFunding() throws Exception {
        String s = sender(), t = agentCarrier(), other = newAgentCarrier(), a = admin();
        String otherSender = login("sender2@example.com", "Sender@123");
        String agent = newAgentCarrier();
        JsonNode trip = trip(t), r = request(s);
        String chat = path(r) + "/messages";
        for (String token : List.of(s, t, a, agent)) {
            call("GET", chat, token, null).andExpect(status().isForbidden());
            call("POST", chat, token, Map.of("body", "Not matched yet")).andExpect(status().isForbidden());
        }
        JsonNode selected = offer(t, r, trip, 40);
        offer(other, r, trip(other), 45);
        call("GET", chat, t, null).andExpect(status().isForbidden());
        accept(s, r, selected);
        for (String token : List.of(s, t, a)) {
            call("GET", chat, token, null).andExpect(status().isForbidden());
            call("POST", chat, token, Map.of("body", "Not paid yet")).andExpect(status().isForbidden());
        }
        assertTrue(ok("GET", path(r), t, null).get("senderContact").isNull());
        ok("POST", path(r) + "/demo-escrow", s, null);
        assertTrue(ok("GET", chat, s, null).get("writable").asBoolean());
        assertFalse(ok("GET", chat, a, null).get("writable").asBoolean());
        JsonNode first = ok("POST", chat, s, Map.of("body", "  Package ready for pickup  "));
        assertEquals("Package ready for pickup", first.get("body").asText());
        assertEquals("SENDER", first.get("authorRole").asText());
        ok("POST", chat, t, Map.of("body", "<script>not executed</script>\nI can collect it."));
        assertEquals(2, chatMessages.findByRequestIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
                UUID.fromString(r.get("id").asText()), Long.MAX_VALUE,
                org.springframework.data.domain.PageRequest.of(0, 50)).size());
        assertEquals(2, ok("GET", chat, a, null).get("messages").size());
        assertEquals(2, ok("GET", chat, s, null).get("messages").get(0).get("sequenceNumber").asInt());
        for (String token : List.of(otherSender, other, agent)) {
            call("GET", chat, token, null).andExpect(status().isForbidden());
            call("POST", chat, token, Map.of("body", "Unauthorized")).andExpect(status().isForbidden());
        }
        call("GET", chat, null, null).andExpect(status().isUnauthorized());
        call("POST", chat, a, Map.of("body", "Admin cannot write")).andExpect(status().isForbidden());
        JsonNode separate = request(s);
        call("GET", path(separate) + "/messages", t, null).andExpect(status().isForbidden());
        JsonNode notification = ok("GET", "/api/package-queue/notifications", s, null);
        assertTrue(notification.findValues("message").stream().anyMatch(value -> value.asText().startsWith("New package chat message.")));
        assertFalse(notification.toString().contains("not executed"));
        move(s, r, "CANCELLED");
        assertEquals(2, ok("GET", chat, t, null).get("messages").size());
        assertFalse(ok("GET", chat, s, null).get("writable").asBoolean());
        call("POST", chat, s, Map.of("body", "Closed")).andExpect(status().isBadRequest());
    }

    @Test
    void chatValidatesMessagesAndPaginatesWithoutCrossPackageLeaks() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode r = matched(s, t, trip(t));
        ok("POST", path(r) + "/demo-escrow", s, null);
        String chat = path(r) + "/messages";
        for (String body : List.of("", " \n\t", "x".repeat(2001)))
            call("POST", chat, s, Map.of("body", body)).andExpect(status().isBadRequest());
        call("POST", chat, s, Map.of()).andExpect(status().isBadRequest());
        call("GET", chat + "?before=0", s, null).andExpect(status().isBadRequest());
        call("GET", chat + "?before=invalid", s, null).andExpect(status().isBadRequest());
        for (int i = 1; i <= 52; i++) ok("POST", chat, s, Map.of("body", "Message " + i));
        JsonNode latest = ok("GET", chat, t, null);
        assertEquals(50, latest.get("messages").size());
        assertEquals("Message 52", latest.get("messages").get(0).get("body").asText());
        assertEquals("Message 3", latest.get("messages").get(49).get("body").asText());
        JsonNode earlier = ok("GET", chat + "?before=" + latest.get("nextBefore").asLong(), t, null);
        assertEquals(2, earlier.get("messages").size());
        assertEquals("Message 2", earlier.get("messages").get(0).get("body").asText());
        assertTrue(earlier.get("nextBefore").isNull());
        JsonNode separate = request(s);
        JsonNode separateMatch = accept(s, separate, offer(t, separate, trip(t), 40));
        ok("POST", path(separateMatch) + "/demo-escrow", s, null);
        assertEquals(0, ok("GET", path(separateMatch) + "/messages", s, null).get("messages").size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousChatMessagesPersistInSequenceOrder() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode r = matched(s, t, trip(t));
        ok("POST", path(r) + "/demo-escrow", s, null);
        String chat = path(r) + "/messages";
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (String token : List.of(s, t)) results.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return call("POST", chat, token, Map.of("body", "Simultaneous message"))
                        .andReturn().getResponse().getStatus();
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Integer> result : results) assertEquals(200, result.get(20, TimeUnit.SECONDS));
        }
        JsonNode persisted = ok("GET", chat, s, null).get("messages");
        assertEquals(2, persisted.size());
        assertEquals(2, persisted.get(0).get("sequenceNumber").asLong());
        assertEquals(1, persisted.get(1).get("sequenceNumber").asLong());
    }

    @Test
    void agentsCanPostOwnTripsWinOffersChatAndCompleteDelivery() throws Exception {
        String s = sender(), t = newAgentCarrier(), agent = login("agent@example.com", "Agent@123"), a = admin();
        JsonNode agentTrip = trip(agent), travelerTrip = trip(t), r = request(s);
        assertEquals("AGENT", agentTrip.get("traveler").get("role").asText());
        JsonNode own = ok("GET", base + "/trips", agent, null);
        assertTrue(own.findValues("id").contains(agentTrip.get("id")));
        assertFalse(own.findValues("id").contains(travelerTrip.get("id")));
        call("GET", base + "/trips/" + travelerTrip.get("id").asText() + "/matches", agent, null)
                .andExpect(status().isForbidden());
        call("POST", path(r) + "/offers", agent, Map.of("tripId", travelerTrip.get("id").asText(), "price", 40))
                .andExpect(status().isForbidden());
        assertEquals(agentTrip.get("id"), ok("GET", path(r) + "/matches", agent, null).get(0).get("trip").get("id"));
        String verifyPath = base + "/travelers/" + agentTrip.get("traveler").get("id").asText() + "/verification";
        assertEquals("ID_VERIFIED", ok("PUT", verifyPath, a, Map.of("level", "ID_VERIFIED")).get("verification").asText());
        accept(s, r, offer(agent, r, agentTrip, 40));
        String chat = path(r) + "/messages";
        ok("POST", path(r) + "/demo-escrow", s, null);
        assertEquals("AGENT", ok("POST", chat, agent, Map.of("body", "Agent pickup arranged")).get("authorRole").asText());
        ok("POST", chat, s, Map.of("body", "Thank you"));
        move(agent, r, "PICKED_UP"); move(agent, r, "IN_TRANSIT"); move(agent, r, "ARRIVED"); confirmDelivery(s, agent, r);
        assertEquals(2, ok("GET", chat, a, null).get("messages").size());
        assertFalse(ok("GET", chat, agent, null).get("writable").asBoolean());
        call("POST", chat, agent, Map.of("body", "Too late")).andExpect(status().isBadRequest());
        ok("POST", path(r) + "/rating", s, Map.of("rating", 5));
    }

    @Test
    void cancellingBeforeFundingNeverUnlocksChat() throws Exception {
        String s = sender(), t = agentCarrier(), a = admin();
        JsonNode r = matched(s, t, trip(t));
        UUID id = UUID.fromString(r.get("id").asText());
        chatMessages.saveAndFlush(com.habeshlist.entity.DeliveryMessage.builder()
                .request(requests.findById(id).orElseThrow())
                .author(users.findByEmailIgnoreCase("sender@example.com").orElseThrow())
                .body("Historical pre-funding message").createdAt(java.time.Instant.now()).sequenceNumber(1).build());
        call("GET", path(r) + "/messages", s, null).andExpect(status().isForbidden());
        move(s, r, "CANCELLED");
        for (String token : List.of(s, t, a)) {
            call("GET", path(r) + "/messages", token, null).andExpect(status().isForbidden());
            call("POST", path(r) + "/messages", token, Map.of("body", "Never funded")).andExpect(status().isForbidden());
        }
        assertEquals(1, chatMessages.findByRequestIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
                id, Long.MAX_VALUE, org.springframework.data.domain.PageRequest.of(0, 50)).size());
    }

    @Test
    void completeLifecycleUsesSimulatedEscrowAndRealRatings() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode trip = trip(t), r = request(s);
        JsonNode offer = offer(t, r, trip, 42);
        assertTrue(ok("GET", path(r), t, null).get("senderContact").isNull());
        assertEquals(1, ok("GET", path(r) + "/matches", s, null).size());
        r = accept(s, r, offer);
        assertEquals("MATCHED", r.get("status").asText());
        assertTrue(ok("GET", path(r), t, null).get("senderContact").isNull());
        call("PUT", path(r) + "/status", t, Map.of("status", "PICKED_UP")).andExpect(status().isBadRequest());
        call("POST", path(r) + "/demo-escrow", t, null).andExpect(status().isForbidden());
        r = ok("POST", path(r) + "/demo-escrow", s, null);
        assertEquals("DEMO_HELD", r.get("escrowStatus").asText());
        assertEquals(42, r.get("escrowAmount").asInt());
        assertEquals("sender@example.com", ok("GET", path(r), t, null).get("senderContact").get("email").asText());
        call("PUT", path(r) + "/status", s, Map.of("status", "PICKED_UP")).andExpect(status().isForbidden());
        move(t, r, "PICKED_UP"); move(t, r, "IN_TRANSIT"); move(t, r, "ARRIVED");
        r = confirmDelivery(s, t, r);
        call("PUT", path(r) + "/status", t, Map.of("status", "COMPLETED")).andExpect(status().isForbidden());
        assertEquals("DEMO_RELEASED", r.get("escrowStatus").asText());
        assertEquals(8, r.get("timeline").size());
        assertEquals(List.of("REQUESTED", "MATCHED", "PAID_ESCROW", "PICKED_UP", "IN_TRANSIT", "ARRIVED", "DELIVERED", "COMPLETED"),
                r.get("timeline").findValues("status").stream().map(JsonNode::asText).toList());
        ok("POST", path(r) + "/rating", s, Map.of("rating", 4));
        call("POST", path(r) + "/rating", s, Map.of("rating", 5)).andExpect(status().isBadRequest());
        JsonNode postedTrip = ok("GET", base + "/trips", t, null).findValues("traveler").stream()
                .filter(value -> value.get("id").asText().equals(trip.get("traveler").get("id").asText())).findFirst().orElseThrow();
        assertEquals(4, postedTrip.get("rating").asDouble());
        assertEquals(1, postedTrip.get("ratingCount").asInt());
    }

    @Test
    void arrivalRequiresTransitAndSelectedCarrierOrAdminAndRemainsActiveUntilDelivery() throws Exception {
        String s = sender(), t = agentCarrier(), a = admin(), outsider = newAgentCarrier();
        JsonNode r = matched(s, t, trip(t));
        String statusPath = path(r) + "/status";
        call("PUT", statusPath, t, Map.of("status", "ARRIVED")).andExpect(status().isBadRequest());
        ok("POST", path(r) + "/demo-escrow", s, null);
        call("PUT", statusPath, a, Map.of("status", "ARRIVED")).andExpect(status().isBadRequest());
        move(t, r, "PICKED_UP");
        call("PUT", statusPath, t, Map.of("status", "ARRIVED")).andExpect(status().isBadRequest());
        move(t, r, "IN_TRANSIT");
        call("PUT", statusPath, t, Map.of("status", "DELIVERED")).andExpect(status().isBadRequest());
        for (String token : List.of(s, outsider, login("traveler@example.com", "Traveler@123")))
            call("PUT", statusPath, token, Map.of("status", "ARRIVED")).andExpect(status().isForbidden());
        long activeBefore = ok("GET", "/api/admin/console/overview", a, null).get("activeShipments").asLong();
        var stored = requests.findById(UUID.fromString(r.get("id").asText())).orElseThrow();
        var owner = users.findById(stored.getAcceptedOffer().getTraveler().getId()).orElseThrow();
        stored.setCreatedByAgent(owner);
        long agentActiveBefore = ok("GET", "/api/agent/dashboard", t, null).get("activeShipments").asLong();
        JsonNode arrived = move(t, r, "ARRIVED");
        assertEquals("ARRIVED", arrived.get("status").asText());
        JsonNode event = arrived.get("timeline").get(arrived.get("timeline").size() - 1);
        assertEquals("ARRIVED", event.get("status").asText());
        assertDoesNotThrow(() -> java.time.Instant.parse(event.get("createdAt").asText()));
        assertEquals(event, ok("GET", path(r), s, null).get("timeline").get(arrived.get("timeline").size() - 1));
        assertEquals(activeBefore, ok("GET", "/api/admin/console/overview", a, null).get("activeShipments").asLong());
        assertEquals(agentActiveBefore, ok("GET", "/api/agent/dashboard", t, null).get("activeShipments").asLong());
        assertTrue(ok("GET", "/api/admin/console/requests?view=tracking&status=ARRIVED", a, null)
                .get("items").findValues("id").contains(r.get("id")));
        assertTrue(ok("GET", "/api/agent/packages?status=ARRIVED", t, null)
                .get("items").findValues("id").contains(r.get("id")));
        for (String invalid : List.of("ARRIVED", "IN_TRANSIT", "PICKED_UP", "COMPLETED", "CANCELLED"))
            call("PUT", statusPath, invalid.equals("COMPLETED") ? s : t, Map.of("status", invalid)).andExpect(status().isBadRequest());
        assertTrue(ok("GET", "/api/package-queue/notifications", s, null).findValues("message").stream()
                .anyMatch(value -> value.asText().contains("status changed to ARRIVED")));
        confirmDelivery(s, a, r);
    }

    @Test
    void legacyDeliveredBookingsCanCompleteWithoutFabricatingArrival() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode r = matched(s, t, trip(t));
        ok("POST", path(r) + "/demo-escrow", s, null);
        var stored = requests.findById(UUID.fromString(r.get("id").asText())).orElseThrow();
        stored.setDeliveryCodeRequired(false);
        stored.setStatus(DeliveryStatus.DELIVERED);
        JsonNode completed = move(s, r, "COMPLETED");
        assertEquals("COMPLETED", completed.get("status").asText());
        assertFalse(completed.get("timeline").findValues("status").stream()
                .anyMatch(value -> value.asText().equals("ARRIVED")));
    }

    @Test
    void adminCanRecordArrivalAndResolveADisputeAfterArrival() throws Exception {
        String s = sender(), t = agentCarrier(), a = admin();
        JsonNode r = matched(s, t, trip(t));
        ok("POST", path(r) + "/demo-escrow", s, null);
        move(t, r, "PICKED_UP"); move(t, r, "IN_TRANSIT"); move(a, r, "ARRIVED");
        JsonNode disputed = ok("PUT", path(r) + "/status", s, Map.of("status", "DISPUTED", "reason", "Handoff delayed"));
        assertEquals("DISPUTED", disputed.get("status").asText());
        call("PUT", path(r) + "/status", t, Map.of("status", "DELIVERED")).andExpect(status().isForbidden());
        JsonNode resolved = ok("PUT", path(r) + "/status", a, Map.of("status", "COMPLETED", "reason", "Confirmed by both parties"));
        assertEquals("COMPLETED", resolved.get("status").asText());
        assertFalse(resolved.get("timeline").findValues("status").stream().anyMatch(value -> value.asText().equals("DELIVERED")));
    }

    @Test
    void matchingEnforcesRouteWindowWeightItemRulesAndActiveTravelers() throws Exception {
        String s = sender(), t = agentCarrier(), second = newAgentCarrier();
        JsonNode good = trip(t), r = request(s);
        for (Map<String, Object> changes : List.<Map<String, Object>>of(
                Map.of("origin", "Elsewhere"), Map.of("destination", "Elsewhere"),
                Map.of("departureDate", date(6), "expectedArrivalDate", date(7)),
                Map.of("departureDate", date(0), "expectedArrivalDate", date(1)),
                Map.of("capacityKg", 3), Map.of("allowedItems", List.of("DOCUMENTS")),
                Map.of("expectedArrivalDate", date(6)))) {
            var input = tripInput(); input.putAll(changes);
            ok("POST", base + "/trips", t, input);
        }
        JsonNode inactive = trip(second);
        users.findById(UUID.fromString(inactive.get("traveler").get("id").asText())).orElseThrow().setActive(false);
        JsonNode matches = ok("GET", path(r) + "/matches", s, null);
        assertEquals(1, matches.size());
        assertEquals(good.get("id"), matches.get(0).get("trip").get("id"));
        var normalized = requestInput(); normalized.put("pickupCity", "  " + origin.toUpperCase() + "   ");
        JsonNode normalizedRequest = ok("POST", base + "/requests", s, normalized);
        assertEquals(1, ok("GET", path(normalizedRequest) + "/matches", s, null).size());
    }

    @Test
    void offersArePrivateAndSenderSelectsExactlyOne() throws Exception {
        String s = sender(), t1 = agentCarrier(), t2 = newAgentCarrier();
        JsonNode r = request(s), trip1 = trip(t1), trip2 = trip(t2);
        JsonNode first = offer(t1, r, trip1, 50), second = offer(t2, r, trip2, 40);
        assertEquals(1, ok("GET", path(r), t1, null).get("offers").size());
        assertEquals(2, ok("GET", path(r), s, null).get("offers").size());
        call("POST", path(r) + "/offers/" + first.get("id").asText() + "/accept", t1, null).andExpect(status().isForbidden());
        JsonNode selected = accept(s, r, second);
        assertEquals(second.get("id"), selected.get("acceptedOfferId"));
        assertEquals("DECLINED", selected.get("offers").findValues("status").stream()
                .map(JsonNode::asText).filter("DECLINED"::equals).findFirst().orElseThrow());
        call("POST", path(r) + "/offers/" + first.get("id").asText() + "/accept", s, null).andExpect(status().isBadRequest());
        call("GET", path(r), t1, null).andExpect(status().isForbidden());
        assertFalse(ok("GET", base + "/requests", t1, null).findValues("id").contains(r.get("id")));
    }

    @Test
    void dmvPickupsMatchAcrossCitiesForSuggestionsOffersAndAcceptance() throws Exception {
        String s = sender(), t = agentCarrier();
        List<String> locations = List.of("Alexandria, VA", "Maryland", "DC", "Washington, D.C.",
                "Silver Spring, MD", "Arlington, Virginia", "VA", "Baltimore", "Alexandira, VA");
        for (int i = 0; i < locations.size(); i++) {
            var tripInput = tripInput();
            tripInput.put("origin", locations.get(i));
            JsonNode trip = ok("POST", base + "/trips", t, tripInput);
            var input = requestInput();
            input.put("pickupCity", locations.get((i + 1) % locations.size()));
            JsonNode r = ok("POST", base + "/requests", s, input);
            assertTrue(ok("GET", base + "/trips/" + trip.get("id").asText() + "/matches", t, null)
                    .findValues("id").contains(r.get("id")));
            assertTrue(ok("GET", path(r) + "/matches", s, null).findValues("id").contains(trip.get("id")));
            assertTrue(ok("GET", path(r) + "/matches", t, null).findValues("id").contains(trip.get("id")));
            assertEquals("MATCHED", accept(s, r, offer(t, r, trip, 50)).get("status").asText());
        }
    }

    @Test
    void regionalRequestsIncludeDepartureAndArrivalDateBoundaries() throws Exception {
        String s = sender(), t = agentCarrier();
        for (int[] dates : List.of(new int[]{3, 4, 3, 4}, new int[]{3, 4, 1, 5},
                new int[]{3, 4, 3, 5}, new int[]{3, 4, 1, 4}, new int[]{3, 3, 3, 3})) {
            var input = tripInput();
            input.put("origin", "Washington, DC");
            input.put("departureDate", date(dates[0]));
            input.put("expectedArrivalDate", date(dates[1]));
            JsonNode trip = ok("POST", base + "/trips", t, input);
            var requestInput = requestInput();
            requestInput.put("pickupCity", "Alexandria, VA");
            requestInput.put("availableFrom", date(dates[2]));
            requestInput.put("deadline", date(dates[3]));
            JsonNode r = ok("POST", base + "/requests", s, requestInput);
            assertTrue(ok("GET", base + "/requests", t, null).findValues("id").contains(r.get("id")));
            assertTrue(ok("GET", base + "/trips/" + trip.get("id").asText() + "/matches", t, null)
                    .findValues("id").contains(r.get("id")));
            assertEquals("MATCHED", accept(s, r, offer(t, r, trip, 50)).get("status").asText());
        }
        var input = tripInput();
        input.put("origin", "Maryland");
        JsonNode trip = ok("POST", base + "/trips", t, input);
        for (int[] window : List.of(new int[]{4, 5}, new int[]{1, 3})) {
            var requestInput = requestInput();
            requestInput.put("pickupCity", "Alexandria, VA");
            requestInput.put("availableFrom", date(window[0]));
            requestInput.put("deadline", date(window[1]));
            JsonNode r = ok("POST", base + "/requests", s, requestInput);
            assertEquals(0, ok("GET", path(r) + "/matches", t, null).size());
            call("POST", path(r) + "/offers", t, Map.of("tripId", trip.get("id").asText(), "price", 50))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void regionalMatchingDoesNotBypassOtherRulesOrIncludeUnrelatedCities() throws Exception {
        String s = sender(), t = agentCarrier();
        var tripInput = tripInput();
        tripInput.put("origin", "Washington, DC");
        JsonNode trip = ok("POST", base + "/trips", t, tripInput);
        var valid = requestInput();
        valid.put("pickupCity", "Alexandria, VA");
        List<Map<String, Object>> invalid = new ArrayList<>();
        for (String city : List.of("Richmond, VA", "Washington, PA", "Seattle", "Richmond")) {
            var input = new HashMap<>(valid); input.put("pickupCity", city); invalid.add(input);
        }
        var destination = new HashMap<>(valid); destination.put("destinationCity", "Maryland"); invalid.add(destination);
        var date = new HashMap<>(valid); date.put("deadline", date(3)); invalid.add(date);
        var weight = new HashMap<>(valid); weight.put("weightKg", 6); invalid.add(weight);
        var item = new HashMap<>(valid); item.put("itemType", "MEDICINE"); invalid.add(item);
        for (var input : invalid) {
            JsonNode r = ok("POST", base + "/requests", s, input);
            assertEquals(0, ok("GET", path(r) + "/matches", t, null).size());
            call("POST", path(r) + "/offers", t, Map.of("tripId", trip.get("id").asText(), "price", 50))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void requestMatchesShowAgentsOnlyTheirCompatibleTrips() throws Exception {
        String s = sender(), t = agentCarrier(), other = newAgentCarrier();
        JsonNode ownTrip = trip(t);
        trip(other);
        var wrongRoute = tripInput();
        wrongRoute.put("origin", "Different city");
        ok("POST", base + "/trips", t, wrongRoute);
        JsonNode r = request(s);
        JsonNode matches = ok("GET", path(r) + "/matches", t, null);
        assertEquals(1, matches.size());
        assertEquals(ownTrip.get("id"), matches.get(0).get("trip").get("id"));
        assertTrue(matches.get(0).get("request").get("senderContact").isNull());
        assertEquals(2, ok("GET", path(r) + "/matches", s, null).size());
        assertEquals("MATCHED", accept(s, r, offer(t, r, ownTrip, 50)).get("status").asText());
        call("GET", path(r) + "/matches", other, null).andExpect(status().isForbidden());
    }

    @Test
    void invalidOffersExplainEachMismatchAndDoNotCreateAnOffer() throws Exception {
        String s = sender(), t = agentCarrier();
        var input = tripInput();
        input.put("origin", "Different pickup");
        input.put("destination", "Different destination");
        input.put("departureDate", date(0));
        input.put("expectedArrivalDate", date(8));
        input.put("capacityKg", 2);
        input.put("allowedItems", List.of("DOCUMENTS"));
        input.put("blockedItems", List.of("CLOTHING"));
        JsonNode invalidTrip = ok("POST", base + "/trips", t, input), r = request(s);
        String error = call("POST", path(r) + "/offers", t,
                Map.of("tripId", invalidTrip.get("id").asText(), "price", 50))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String message = om.readTree(error).get("message").asText();
        assertTrue(message.contains("must match pickup city"));
        assertTrue(message.contains("must match destination city"));
        assertTrue(message.contains("before the package is available"));
        assertTrue(message.contains("after the delivery deadline"));
        assertTrue(message.contains("kg available but the package requires"));
        assertTrue(message.contains("does not allow item type CLOTHING"));
        assertEquals(0, ok("GET", path(r) + "/matches", t, null).size());
        assertEquals(0, ok("GET", path(r), s, null).get("offers").size());
    }

    @Test
    void capacityIsRecheckedAtAcceptanceAndRefundRestoresIt() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode trip = trip(t), first = request(s), second = request(s);
        JsonNode offer1 = offer(t, first, trip, 40), offer2 = offer(t, second, trip, 50);
        accept(s, first, offer1);
        call("POST", path(second) + "/offers/" + offer2.get("id").asText() + "/accept", s, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("kg available but the package requires")));
        ok("POST", path(first) + "/demo-escrow", s, null);
        JsonNode cancelled = move(s, first, "CANCELLED");
        assertEquals("DEMO_REFUNDED", cancelled.get("escrowStatus").asText());
        call("PUT", path(first) + "/status", s, Map.of("status", "CANCELLED")).andExpect(status().isBadRequest());
        assertEquals("MATCHED", accept(s, second, offer2).get("status").asText());
    }

    @Test
    void disputesFreezeEscrowUntilAdminResolvesWithReason() throws Exception {
        String s = sender(), t = agentCarrier(), a = admin();
        JsonNode trip = trip(t), r = matched(s, t, trip);
        ok("POST", path(r) + "/demo-escrow", s, null);
        move(t, r, "PICKED_UP");
        call("PUT", path(r) + "/status", s, Map.of("status", "CANCELLED")).andExpect(status().isBadRequest());
        call("PUT", path(r) + "/status", s, Map.of("status", "DISPUTED")).andExpect(status().isBadRequest());
        r = ok("PUT", path(r) + "/status", s, Map.of("status", "DISPUTED", "reason", "Parcel damaged"));
        assertEquals("DEMO_HELD", r.get("escrowStatus").asText());
        call("PUT", path(r) + "/status", s, Map.of("status", "COMPLETED")).andExpect(status().isForbidden());
        call("PUT", path(r) + "/status", a, Map.of("status", "CANCELLED")).andExpect(status().isBadRequest());
        r = ok("PUT", path(r) + "/status", a, Map.of("status", "CANCELLED", "reason", "Refund approved in demo"));
        assertEquals("DEMO_REFUNDED", r.get("escrowStatus").asText());
        assertEquals(0, trips.findById(UUID.fromString(trip.get("id").asText())).orElseThrow().getReservedKg().signum());
    }

    @Test
    void adminCanResolveDisputeByReleasingDemoEscrowWithoutDoubleFunding() throws Exception {
        String s = sender(), t = agentCarrier(), a = admin();
        JsonNode r = matched(s, t, trip(t));
        ok("POST", path(r) + "/demo-escrow", s, null);
        call("POST", path(r) + "/demo-escrow", s, null).andExpect(status().isBadRequest());
        ok("PUT", path(r) + "/status", t, Map.of("status", "DISPUTED", "reason", "Delivery confirmation disputed"));
        JsonNode resolved = ok("PUT", path(r) + "/status", a, Map.of("status", "COMPLETED", "reason", "Delivery verified for demo resolution"));
        assertEquals("DEMO_RELEASED", resolved.get("escrowStatus").asText());
        call("PUT", path(r) + "/status", a, Map.of("status", "CANCELLED", "reason", "Repeated resolution")).andExpect(status().isBadRequest());
        call("POST", path(r) + "/demo-escrow", s, null).andExpect(status().isBadRequest());
    }

    @Test
    void rankingUsesPriceRatingAndAdminVerification() throws Exception {
        String s = sender(), t1 = agentCarrier(), t2 = newAgentCarrier(), a = admin();
        JsonNode trip1 = trip(t1), trip2 = trip(t2), r = request(s);
        offer(t1, r, trip1, 40); offer(t2, r, trip2, 50);
        JsonNode before = ok("GET", path(r), s, null);
        assertEquals(trip1.get("traveler").get("id"), before.get("offers").get(0).get("traveler").get("id"));
        String verifyPath = base + "/travelers/" + trip2.get("traveler").get("id").asText() + "/verification";
        call("PUT", verifyPath, t2, Map.of("level", "FULLY_VERIFIED")).andExpect(status().isForbidden());
        ok("PUT", verifyPath, a, Map.of("level", "FULLY_VERIFIED"));
        JsonNode verified = ok("GET", path(r), s, null);
        assertEquals(60, verified.get("offers").get(0).get("score").asDouble());
        assertEquals(trip2.get("traveler").get("id"), verified.get("offers").get(0).get("traveler").get("id"));
        JsonNode completed = matched(s, t1, trip1);
        var entity = requests.findById(UUID.fromString(completed.get("id").asText())).orElseThrow();
        entity.setStatus(DeliveryStatus.COMPLETED);
        entity.setRating(5);
        JsonNode rated = ok("GET", path(r), s, null);
        assertEquals(80, rated.get("offers").get(0).get("score").asDouble());
        assertEquals(trip1.get("traveler").get("id"), rated.get("offers").get(0).get("traveler").get("id"));
    }

    @Test
    void agentSuggestionsFavorHigherPricesAndDoNotReserveWeight() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode trip = trip(t), low = request(s);
        var input = requestInput(); input.put("offeredPrice", 100);
        JsonNode high = ok("POST", base + "/requests", s, input);
        JsonNode matches = ok("GET", base + "/trips/" + trip.get("id").asText() + "/matches", t, null);
        assertEquals(2, matches.size());
        assertEquals(high.get("id"), matches.get(0).get("request").get("id"));
        assertEquals(50, matches.get(0).get("score").asDouble());
        offer(t, low, trip, 50);
        assertEquals(0, trips.findById(UUID.fromString(trip.get("id").asText())).orElseThrow().getReservedKg().signum());
    }

    @Test
    void validatesTripRulesDatesAndNumericFields() throws Exception {
        String t = agentCarrier(), s = sender();
        var input = tripInput(); input.put("allowedItems", List.of("FOOD"));
        call("POST", base + "/trips", t, input).andExpect(status().isBadRequest());
        input = tripInput(); input.put("allowedItems", List.of());
        call("POST", base + "/trips", t, input).andExpect(status().isBadRequest());
        input = tripInput(); input.put("expectedArrivalDate", date(1));
        call("POST", base + "/trips", t, input).andExpect(status().isBadRequest());
        var req = requestInput(); req.put("deadline", date(0));
        call("POST", base + "/requests", s, req).andExpect(status().isBadRequest());
        req = requestInput(); req.put("offeredPrice", 0);
        call("POST", base + "/requests", s, req).andExpect(status().isBadRequest());
        req = requestInput(); req.put("weightKg", 0);
        call("POST", base + "/requests", s, req).andExpect(status().isBadRequest());
        req = requestInput(); req.put("itemType", "INVALID");
        call("POST", base + "/requests", s, req).andExpect(status().isBadRequest());
    }

    @Test
    void agentsBrowseSenderRequestsButNotOtherCarriersTrips() throws Exception {
        String s1 = sender(), s2 = login("sender2@example.com", "Sender@123");
        String t1 = agentCarrier(), t2 = newAgentCarrier(), a = admin();
        JsonNode trip1 = trip(t1), trip2 = trip(t2), request1 = request(s1), request2 = request(s2);
        JsonNode ownTrips = ok("GET", base + "/trips", t1, null);
        assertTrue(ownTrips.findValues("id").contains(trip1.get("id")));
        assertFalse(ownTrips.findValues("id").contains(trip2.get("id")));
        for (JsonNode item : ownTrips) assertEquals(trip1.get("traveler").get("id"), item.get("traveler").get("id"));
        JsonNode secondTrips = ok("GET", base + "/trips", t2, null);
        assertTrue(secondTrips.findValues("id").contains(trip2.get("id")));
        assertFalse(secondTrips.findValues("id").contains(trip1.get("id")));
        JsonNode senderTrips = ok("GET", base + "/trips", s1, null);
        assertTrue(senderTrips.findValues("id").containsAll(List.of(trip1.get("id"), trip2.get("id"))));
        JsonNode travelerRequests = ok("GET", base + "/requests", t1, null);
        assertTrue(travelerRequests.findValues("id").containsAll(List.of(request1.get("id"), request2.get("id"))));
        assertFalse(ok("GET", base + "/requests", s1, null).findValues("id").contains(request2.get("id")));
        call("GET", path(request2), s1, null).andExpect(status().isForbidden());
        call("GET", base + "/trips/" + trip2.get("id").asText() + "/matches", t1, null).andExpect(status().isForbidden());
        assertTrue(ok("GET", base + "/trips", a, null).findValues("id").containsAll(List.of(trip1.get("id"), trip2.get("id"))));
    }

    @Test
    void senderListsOnlyTheirOwnCreatedRequestsAcrossStatuses() throws Exception {
        String firstSender = sender(), secondSender = login("sender2@example.com", "Sender@123");
        JsonNode first = request(firstSender), second = request(secondSender);
        move(firstSender, first, "CANCELLED");
        JsonNode firstList = ok("GET", base + "/requests", firstSender, null);
        assertTrue(firstList.findValues("id").contains(first.get("id")));
        assertFalse(firstList.findValues("id").contains(second.get("id")));
        for (JsonNode item : firstList) assertEquals(first.get("senderId"), item.get("senderId"));
        JsonNode secondList = ok("GET", base + "/requests", secondSender, null);
        assertTrue(secondList.findValues("id").contains(second.get("id")));
        assertFalse(secondList.findValues("id").contains(first.get("id")));
        for (JsonNode item : secondList) assertEquals(second.get("senderId"), item.get("senderId"));
    }

    @Test
    void roleOwnershipAndAnonymousAccessAreEnforced() throws Exception {
        String s = sender(), t = agentCarrier(), a = admin(), other = login("sender2@example.com", "Sender@123");
        call("GET", base + "/requests", null, null).andExpect(status().isUnauthorized());
        call("POST", base + "/trips", s, tripInput()).andExpect(status().isForbidden());
        call("POST", base + "/requests", t, requestInput()).andExpect(status().isForbidden());
        JsonNode trip = trip(t), r = request(s);
        call("GET", path(r), other, null).andExpect(status().isForbidden());
        call("GET", path(r) + "/matches", other, null).andExpect(status().isForbidden());
        assertEquals(trip.get("id"), ok("GET", path(r) + "/matches", t, null).get(0).get("trip").get("id"));
        call("GET", base + "/trips/" + trip.get("id").asText() + "/matches", s, null).andExpect(status().isForbidden());
        JsonNode all = ok("GET", base + "/requests", a, null);
        assertTrue(all.findValues("id").contains(r.get("id")));
        String stranger = newAgentCarrier();
        call("POST", path(r) + "/offers", stranger, Map.of("tripId", trip.get("id").asText(), "price", 50)).andExpect(status().isForbidden());
        call("POST", path(r) + "/rating", s, Map.of("rating", 5)).andExpect(status().isBadRequest());
    }

    @Test
    void offerWithdrawalAndDeclineAllowAnotherOfferButNotDuplicates() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode trip = trip(t), r = request(s), first = offer(t, r, trip, 45);
        call("POST", path(r) + "/offers", t, Map.of("tripId", trip.get("id").asText(), "price", 44)).andExpect(status().isBadRequest());
        ok("POST", path(r) + "/offers/" + first.get("id").asText() + "/withdraw", t, null);
        JsonNode second = offer(t, r, trip, 44);
        ok("POST", path(r) + "/offers/" + second.get("id").asText() + "/decline", s, null);
        offer(t, r, trip, 43);
        assertEquals(1, ok("GET", path(r), s, null).get("offers").size());
    }

    @Test
    void queueMutationsAreRetiredAndNotificationsReachSenders() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode r = request(s), trip = trip(t);
        offer(t, r, trip, 40);
        JsonNode messages = ok("GET", "/api/package-queue/notifications", s, null);
        assertTrue(messages.findValues("requestId").contains(r.get("id")));
        for (String action : List.of("claim", "decision"))
            call("POST", "/api/package-queue/" + UUID.randomUUID() + "/" + action, t, Map.of()).andExpect(status().isNotFound());
        call("POST", "/api/package-queue/" + UUID.randomUUID() + "/payment-proof", t, Map.of()).andExpect(status().isMethodNotAllowed());
        call("GET", "/api/package-queue", t, null).andExpect(status().isForbidden());
    }

    @Test
    void publicBrowseIncludesRealReputationAndPricesWithoutExposingIdentityOrFreeText() throws Exception {
        String s = sender(), t = newAgentCarrier(), a = admin();
        var input = tripInput();
        input.put("handoffLocation", "PRIVATE_HANDOFF");
        input.put("notes", "PRIVATE_NOTES");
        input.put("itemPrices", List.of(
                Map.of("category", "CLOTHING", "price", 3.25, "unit", "PER_KG"),
                Map.of("category", "DOCUMENTS", "price", 10, "unit", "PER_ITEM")));
        JsonNode trip = ok("POST", base + "/trips", t, input);
        JsonNode unrated = null;
        for (JsonNode row : ok("GET", "/api/public/travel", null, null))
            if (row.get("id").equals(trip.get("id"))) unrated = row;
        assertNotNull(unrated);
        assertTrue(unrated.get("rating").isNull());
        assertEquals(0, unrated.get("ratingCount").asInt());
        assertEquals("UNVERIFIED", unrated.get("verification").asText());
        assertEquals(date(4), unrated.get("expectedArrivalDate").asText());
        assertEquals(2, unrated.get("allowedItems").size());
        assertEquals(trip.get("itemPrices"), unrated.get("itemPrices"));
        String userId = trip.get("traveler").get("id").asText();
        for (int rating : List.of(5, 3)) {
            JsonNode r = matched(s, t, trip);
            var stored = requests.findById(UUID.fromString(r.get("id").asText())).orElseThrow();
            stored.setStatus(DeliveryStatus.COMPLETED);
            stored.setRating(rating);
            trips.findById(UUID.fromString(trip.get("id").asText())).orElseThrow().setReservedKg(java.math.BigDecimal.ZERO);
        }
        for (String level : List.of("ID_VERIFIED", "FULLY_VERIFIED", "UNVERIFIED")) {
            ok("PUT", base + "/travelers/" + userId + "/verification", a, Map.of("level", level));
            JsonNode rated = null;
            for (JsonNode row : ok("GET", "/api/public/travel", null, null))
                if (row.get("id").equals(trip.get("id"))) rated = row;
            assertNotNull(rated);
            assertEquals(4, rated.get("rating").asDouble());
            assertEquals(2, rated.get("ratingCount").asInt());
            assertEquals(level, rated.get("verification").asText());
            for (String field : List.of("name", "traveler", "travelerId", "photo", "email", "phone", "handoffLocation", "notes"))
                assertFalse(rated.has(field), field);
            assertFalse(rated.toString().contains(userId));
            assertFalse(rated.toString().contains("PRIVATE_"));
        }
    }

    @Test
    void tripDetailsKeepPublicProjectionPrivateAndAuthenticatedVisibilityScoped() throws Exception {
        String s = sender(), t = newAgentCarrier(), other = agentCarrier(), a = admin();
        var input = tripInput();
        input.put("handoffLocation", "PRIVATE_HANDOFF");
        input.put("notes", "PRIVATE_NOTES");
        input.put("itemPrices", List.of(
                Map.of("category", "CLOTHING", "price", 3.25, "unit", "PER_KG"),
                Map.of("category", "DOCUMENTS", "price", 10, "unit", "PER_ITEM")));
        JsonNode saved = ok("POST", base + "/trips", t, input);
        String id = saved.get("id").asText();
        JsonNode summary = ok("GET", "/api/public/travel/" + id, null, null);
        JsonNode listed = null;
        for (JsonNode row : ok("GET", "/api/public/travel", null, null))
            if (row.get("id").equals(saved.get("id"))) listed = row;
        assertEquals(listed, summary);
        assertFalse(summary.toString().contains("PRIVATE_"));
        assertFalse(summary.toString().contains(saved.get("traveler").get("id").asText()));
        for (String token : List.of(s, t, a)) {
            JsonNode detail = ok("GET", base + "/trips/" + id, token, null);
            assertEquals("PRIVATE_HANDOFF", detail.get("handoffLocation").asText());
            assertEquals("PRIVATE_NOTES", detail.get("notes").asText());
            assertEquals(saved.get("itemPrices"), detail.get("itemPrices"));
            if (token.equals(s)) {
                assertEquals("Private agent", detail.get("traveler").get("name").asText());
                assertFalse(detail.get("traveler").has("email"));
                assertFalse(detail.get("traveler").has("phone"));
            }
        }
        call("GET", base + "/trips/" + id, null, null).andExpect(status().isUnauthorized());
        call("GET", base + "/trips/" + id, other, null).andExpect(status().isForbidden());
        call("GET", base + "/trips/" + id, login("traveler@example.com", "Traveler@123"), null)
                .andExpect(status().isForbidden());
        call("GET", base + "/trips/" + UUID.randomUUID(), s, null).andExpect(status().isNotFound());
        call("GET", "/api/public/travel/not-a-uuid", null, null).andExpect(status().isBadRequest());
    }

    @Test
    void publicTripDetailsRejectMissingFullPastAndInactiveTrips() throws Exception {
        String t = newAgentCarrier(), s = sender();
        JsonNode saved = trip(t);
        UUID id = UUID.fromString(saved.get("id").asText());
        String url = "/api/public/travel/" + id;
        var stored = trips.findById(id).orElseThrow();
        ok("GET", url, null, null);
        stored.setReservedKg(stored.getCapacityKg());
        call("GET", url, null, null).andExpect(status().isNotFound());
        stored.setReservedKg(java.math.BigDecimal.ZERO);
        stored.setDepartureDate(LocalDate.now().minusDays(1));
        call("GET", url, null, null).andExpect(status().isNotFound());
        call("GET", base + "/trips/" + id, s, null).andExpect(status().isForbidden());
        ok("GET", base + "/trips/" + id, t, null);
        stored.setDepartureDate(LocalDate.now());
        ok("GET", url, null, null);
        stored.getTraveler().setActive(false);
        call("GET", url, null, null).andExpect(status().isNotFound());
        call("GET", base + "/trips/" + id, s, null).andExpect(status().isForbidden());
        call("GET", "/api/public/travel/" + UUID.randomUUID(), null, null).andExpect(status().isNotFound());
    }

    @Test
    void publicTripSummariesExcludePrivateFieldsAndUnavailableTrips() throws Exception {
        String s = sender(), t = agentCarrier();
        var input = tripInput(); input.put("capacityKg", 4);
        JsonNode trip = ok("POST", base + "/trips", t, input);
        JsonNode data = ok("GET", "/api/public/travel", null, null);
        assertTrue(data.findValues("id").contains(trip.get("id")));
        for (JsonNode summary : data)
            assertEquals(Set.of("id", "origin", "destination", "departureDate", "availableCapacityKg", "createdAt",
                            "expectedArrivalDate", "rating", "ratingCount", "verification", "allowedItems", "itemPrices"),
                    om.convertValue(summary, Map.class).keySet());
        matched(s, t, trip);
        assertFalse(ok("GET", "/api/public/travel", null, null).findValues("id").contains(trip.get("id")));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousAcceptancesCannotOverbookOneTrip() throws Exception {
        String s = sender(), t = agentCarrier();
        JsonNode trip = trip(t), r1 = request(s), r2 = request(s);
        JsonNode o1 = offer(t, r1, trip, 40), o2 = offer(t, r2, trip, 45);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (JsonNode[] pair : List.of(new JsonNode[]{r1, o1}, new JsonNode[]{r2, o2})) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return call("POST", path(pair[0]) + "/offers/" + pair[1].get("id").asText() + "/accept", s, null)
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 200).count());
            assertEquals(1, statuses.stream().filter(status -> status == 400).count());
        }
        assertEquals(4, trips.findById(UUID.fromString(trip.get("id").asText())).orElseThrow().getReservedKg().intValueExact());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousOffersCannotBothBeSelectedForOneRequest() throws Exception {
        String s = sender(), t1 = agentCarrier(), t2 = newAgentCarrier();
        JsonNode trip1 = trip(t1), trip2 = trip(t2), r = request(s);
        JsonNode o1 = offer(t1, r, trip1, 40), o2 = offer(t2, r, trip2, 45);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (JsonNode o : List.of(o1, o2)) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return call("POST", path(r) + "/offers/" + o.get("id").asText() + "/accept", s, null)
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 200).count());
            assertEquals(1, statuses.stream().filter(status -> status == 400).count());
        }
        JsonNode selected = ok("GET", path(r), s, null);
        assertEquals(1, selected.get("offers").findValues("status").stream().filter(value -> value.asText().equals("ACCEPTED")).count());
    }
}
