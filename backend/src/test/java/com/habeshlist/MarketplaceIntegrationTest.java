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
    @Autowired com.habeshlist.service.DataSeeder seeder;
    private final String origin = "Origin-" + UUID.randomUUID();
    private final String destination = "Destination-" + UUID.randomUUID();
    private final String base = "/api/marketplace";
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
    private String traveler() throws Exception { return login("traveler@example.com", "Traveler@123"); }
    private String admin() throws Exception { return login("admin@habeshlist.com", "Admin@123"); }
    private String newTraveler() throws Exception {
        return ok("POST", "/api/auth/register", null, Map.of("email", UUID.randomUUID() + "@example.com",
                "password", "New-traveler-password-2026", "firstName", "Second", "lastName", "Traveler", "role", "TRAVELER"))
                .get("token").asText();
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
    private JsonNode matched(String sender, String traveler, JsonNode trip) throws Exception {
        JsonNode r = request(sender);
        return accept(sender, r, offer(traveler, r, trip, 40));
    }

    @Test
    void namesStayPrivateUntilAcceptanceAndContactsUntilFundingForTravelersAndAgents() throws Exception {
        String s = sender(), a = admin(), other = newTraveler();
        for (String selected : List.of(traveler(), login("agent@example.com", "Agent@123"))) {
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
    void packageChatUnlocksOnlyForSelectedParticipantsAndAdminsBeforeFunding() throws Exception {
        String s = sender(), t = traveler(), other = newTraveler(), a = admin();
        String otherSender = login("sender2@example.com", "Sender@123");
        String agent = login("agent@example.com", "Agent@123");
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
        assertTrue(ok("GET", chat, s, null).get("writable").asBoolean());
        assertFalse(ok("GET", chat, a, null).get("writable").asBoolean());
        assertTrue(ok("GET", path(r), t, null).get("senderContact").isNull());
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
        String s = sender(), t = traveler();
        JsonNode r = matched(s, t, trip(t));
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
        assertEquals(0, ok("GET", path(separateMatch) + "/messages", s, null).get("messages").size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousChatMessagesPersistInSequenceOrder() throws Exception {
        String s = sender(), t = traveler();
        JsonNode r = matched(s, t, trip(t));
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
        String s = sender(), t = traveler(), agent = login("agent@example.com", "Agent@123"), a = admin();
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
        assertEquals("AGENT", ok("POST", chat, agent, Map.of("body", "Agent pickup arranged")).get("authorRole").asText());
        ok("POST", chat, s, Map.of("body", "Thank you"));
        ok("POST", path(r) + "/demo-escrow", s, null);
        move(agent, r, "PICKED_UP"); move(agent, r, "IN_TRANSIT"); move(agent, r, "DELIVERED"); move(s, r, "COMPLETED");
        assertEquals(2, ok("GET", chat, a, null).get("messages").size());
        assertFalse(ok("GET", chat, agent, null).get("writable").asBoolean());
        call("POST", chat, agent, Map.of("body", "Too late")).andExpect(status().isBadRequest());
        ok("POST", path(r) + "/rating", s, Map.of("rating", 5));
    }

    @Test
    void completeLifecycleUsesSimulatedEscrowAndRealRatings() throws Exception {
        String s = sender(), t = traveler();
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
        move(t, r, "PICKED_UP"); move(t, r, "IN_TRANSIT"); move(t, r, "DELIVERED");
        call("PUT", path(r) + "/status", t, Map.of("status", "COMPLETED")).andExpect(status().isForbidden());
        r = move(s, r, "COMPLETED");
        assertEquals("DEMO_RELEASED", r.get("escrowStatus").asText());
        assertEquals(7, r.get("timeline").size());
        assertEquals(List.of("REQUESTED", "MATCHED", "PAID_ESCROW", "PICKED_UP", "IN_TRANSIT", "DELIVERED", "COMPLETED"),
                r.get("timeline").findValues("status").stream().map(JsonNode::asText).toList());
        ok("POST", path(r) + "/rating", s, Map.of("rating", 4));
        call("POST", path(r) + "/rating", s, Map.of("rating", 5)).andExpect(status().isBadRequest());
        JsonNode postedTrip = ok("GET", base + "/trips", t, null).findValues("traveler").stream()
                .filter(value -> value.get("id").asText().equals(trip.get("traveler").get("id").asText())).findFirst().orElseThrow();
        assertEquals(4, postedTrip.get("rating").asDouble());
        assertEquals(1, postedTrip.get("ratingCount").asInt());
    }

    @Test
    void matchingEnforcesRouteWindowWeightItemRulesAndActiveTravelers() throws Exception {
        String s = sender(), t = traveler(), second = newTraveler();
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
        String s = sender(), t1 = traveler(), t2 = newTraveler();
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
        String s = sender(), t = traveler();
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
        String s = sender(), t = traveler();
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
        String s = sender(), t = traveler();
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
    void requestMatchesShowTravelersOnlyTheirCompatibleTrips() throws Exception {
        String s = sender(), t = traveler(), other = newTraveler();
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
        String s = sender(), t = traveler();
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
        String s = sender(), t = traveler();
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
        String s = sender(), t = traveler(), a = admin();
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
        String s = sender(), t = traveler(), a = admin();
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
        String s = sender(), t1 = traveler(), t2 = newTraveler(), a = admin();
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
    void travelerSuggestionsFavorHigherPricesAndDoNotReserveWeight() throws Exception {
        String s = sender(), t = traveler();
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
        String t = traveler(), s = sender();
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
    void travelersBrowseSenderRequestsButNotOtherTravelersTrips() throws Exception {
        String s1 = sender(), s2 = login("sender2@example.com", "Sender@123");
        String t1 = traveler(), t2 = newTraveler(), a = admin();
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
        String s = sender(), t = traveler(), a = admin(), other = login("sender2@example.com", "Sender@123");
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
        String stranger = newTraveler();
        call("POST", path(r) + "/offers", stranger, Map.of("tripId", trip.get("id").asText(), "price", 50)).andExpect(status().isForbidden());
        call("POST", path(r) + "/rating", s, Map.of("rating", 5)).andExpect(status().isBadRequest());
    }

    @Test
    void offerWithdrawalAndDeclineAllowAnotherOfferButNotDuplicates() throws Exception {
        String s = sender(), t = traveler();
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
        String s = sender(), t = traveler();
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
    void publicTripSummariesExcludePrivateFieldsAndUnavailableTrips() throws Exception {
        String s = sender(), t = traveler();
        var input = tripInput(); input.put("capacityKg", 4);
        JsonNode trip = ok("POST", base + "/trips", t, input);
        JsonNode data = ok("GET", "/api/public/travel", null, null);
        assertTrue(data.findValues("id").contains(trip.get("id")));
        for (JsonNode summary : data)
            assertEquals(Set.of("id", "origin", "destination", "departureDate", "availableCapacityKg"),
                    om.convertValue(summary, Map.class).keySet());
        matched(s, t, trip);
        assertFalse(ok("GET", "/api/public/travel", null, null).findValues("id").contains(trip.get("id")));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousAcceptancesCannotOverbookOneTrip() throws Exception {
        String s = sender(), t = traveler();
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
        String s = sender(), t1 = traveler(), t2 = newTraveler();
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
