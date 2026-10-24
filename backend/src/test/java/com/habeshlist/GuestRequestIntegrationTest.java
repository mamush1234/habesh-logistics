package com.habeshlist;

import com.fasterxml.jackson.databind.*;
import com.habeshlist.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GuestRequestIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired GuestRequestLinkRepository links;
    @Autowired DeliveryRequestRepository requests;
    @Autowired ShipmentRepository shipments;
    @Autowired jakarta.persistence.EntityManager entityManager;
    private final String adminBase = "/api/admin/guest-request-links";
    private final String publicBase = "/api/guest-requests";
    private final String route = "Guest-" + UUID.randomUUID();

    private ResultActions call(String method, String path, String token, Object body) throws Exception {
        var builder = method.equals("GET") ? get(path) : post(path);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return mvc.perform(builder);
    }
    private JsonNode ok(String method, String path, String token, Object body) throws Exception {
        return om.readTree(call(method, path, token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private String login(String email, String password) throws Exception {
        return ok("POST", "/api/auth/login", null, Map.of("email", email, "password", password)).get("token").asText();
    }
    private String admin() throws Exception { return login("admin@habeshlist.com", "Admin@123"); }
    private String sender() throws Exception { return login("sender@example.com", "Sender@123"); }
    private String traveler() throws Exception { return login("traveler@example.com", "Traveler@123"); }
    private JsonNode link(String admin) throws Exception {
        return ok("POST", adminBase, admin, Map.of("label", "Private sender " + route));
    }
    private String photo() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
    private Map<String, Object> requestInput() {
        return new HashMap<>(Map.of("pickupCity", route, "destinationCity", "Addis Ababa",
                "availableFrom", LocalDate.now().plusDays(1).toString(), "deadline", LocalDate.now().plusDays(7).toString(),
                "itemType", "DOCUMENTS", "description", "Guest parcel", "weightKg", 1, "offeredPrice", 25));
    }
    private Map<String, Object> submission(String token) throws Exception {
        return new HashMap<>(Map.of("token", token, "name", "Guest Sender", "email", "guest@example.com",
                "phone", "+1 (202) 555-0123", "photoDataUrl", photo(), "request", requestInput()));
    }
    private JsonNode requestFor(JsonNode link, String admin) throws Exception {
        JsonNode row = ok("GET", adminBase, admin, null).findParents("id").stream()
                .filter(n -> n.path("id").asText().equals(link.path("link").path("id").asText())).findFirst().orElseThrow();
        return ok("GET", "/api/marketplace/requests/" + row.path("requestId").asText(), admin, null);
    }
    private Map<String, Object> conversionInput() {
        return new HashMap<>(Map.of("recipient", Map.of("name", "Receiver", "phone", "+251911123456",
                "city", "Addis Ababa", "address", "Bole"), "destinationCountry", "Ethiopia",
                "pickupMethod", "ALEXANDRIA_DROPOFF"));
    }
    private JsonNode guestRequest(String admin) throws Exception {
        JsonNode created = link(admin);
        ok("POST", publicBase + "/submit", null, submission(created.path("token").asText()));
        return requestFor(created, admin);
    }
    private String convertPath(JsonNode request) {
        return "/api/admin/guest-requests/" + request.path("id").asText() + "/shipment";
    }
    private JsonNode putOk(String path, String token, Object input) throws Exception {
        return om.readTree(mvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(input)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void conversionCarriesDetailsAndPhotoCreatesTrackingAndPreservesGuestPrivacy() throws Exception {
        String a = admin(), s = sender(), t = traveler();
        JsonNode guest = guestRequest(a);
        call("POST", convertPath(guest), null, conversionInput()).andExpect(status().isUnauthorized());
        for (String user : List.of(s, t, login("agent@example.com", "Agent@123")))
            call("POST", convertPath(guest), user, conversionInput()).andExpect(status().isForbidden());
        long before = shipments.count();
        JsonNode shipment = ok("POST", convertPath(guest), a, conversionInput());
        String id = shipment.path("id").asText(), path = "/api/shipments/" + id;
        assertTrue(shipment.path("trackingNumber").asText().startsWith("HL-"));
        assertTrue(shipment.path("senderId").isNull());
        assertEquals(guest.path("id"), shipment.path("sourceRequestId"));
        assertEquals(guest.path("description"), shipment.path("description"));
        assertEquals(guest.path("weightKg").decimalValue(), shipment.path("declaredWeightKg").decimalValue());
        assertEquals("DOCUMENT", shipment.path("packageType").asText());
        assertEquals("READY_FOR_PICKUP", shipment.path("status").asText());
        assertEquals("PENDING", shipment.path("paymentStatus").asText());
        assertEquals(0, new java.math.BigDecimal(ok("GET", "/api/public/info", null, null).path("documentFee").asText())
                .compareTo(shipment.path("shippingFee").decimalValue()));
        assertEquals(1, shipment.path("photoCount").asInt());
        assertEquals(1, ok("GET", path + "/photos", a, null).path("photos").size());
        assertEquals(shipment.path("id"), ok("POST", convertPath(guest), a, conversionInput()).path("id"));
        entityManager.flush();
        entityManager.clear();
        assertEquals(before + 1, shipments.count());
        JsonNode closed = ok("GET", "/api/marketplace/requests/" + guest.path("id").asText(), a, null);
        assertEquals("CANCELLED", closed.path("status").asText());
        assertEquals(shipment.path("id"), closed.path("convertedShipmentId"));
        assertEquals(shipment.path("trackingNumber"), closed.path("shipmentTrackingNumber"));
        assertEquals("guest@example.com", closed.path("senderContact").path("email").asText());
        call("GET", path, s, null).andExpect(status().isForbidden());
        call("GET", path, t, null).andExpect(status().isForbidden());
        mvc.perform(put(path + "/photos").header("Authorization", "Bearer " + s)
                .contentType(MediaType.APPLICATION_JSON).content("{\"photoDataUrls\":[]}")).andExpect(status().isForbidden());
        JsonNode travelerUser = ok("GET", "/api/admin/users?role=TRAVELER", a, null).get(0);
        putOk(path + "/traveler", a, Map.of("travelerId", travelerUser.path("id").asText()));
        JsonNode view = ok("GET", path, t, null);
        assertFalse(view.toString().contains("guest@example.com"));
        assertEquals("Guest sender", view.path("senderName").asText());
        call("GET", "/api/package-queue/" + id + "/sender", t, null).andExpect(status().isForbidden());
        assertEquals("guest@example.com", ok("GET", "/api/package-queue/" + id + "/sender", a, null).path("email").asText());
        putOk(path + "/payment", a, Map.of("paymentStatus", "PAID"));
        putOk(path + "/status", a, Map.of("status", "RECEIVED_AT_ALEXANDRIA", "location", "Alexandria"));
        assertTrue(ok("GET", path + "/tracking", a, null).size() >= 3);
        mvc.perform(delete(path).header("Authorization", "Bearer " + a)).andExpect(status().isBadRequest());
    }

    @Test
    void conversionFollowsLuggageTripAndManifestWorkflow() throws Exception {
        String a = admin(), t = traveler();
        JsonNode shipment = ok("POST", convertPath(guestRequest(a)), a, conversionInput());
        String sid = shipment.path("id").asText();
        String travelerId = ok("GET", "/api/admin/users?role=TRAVELER", a, null).get(0).path("id").asText();
        JsonNode trip = ok("POST", "/api/trips", a, Map.of("travelerId", travelerId, "departureAirport", "IAD",
                "arrivalAirport", "ADD", "departureDate", LocalDate.now().plusDays(3).toString(),
                "expectedArrivalDate", LocalDate.now().plusDays(4).toString(), "totalLuggageCapacity", 10, "numberOfLuggage", 1));
        String lid = trip.path("luggage").get(0).path("id").asText();
        ok("POST", "/api/luggage/" + lid + "/shipments", a, Map.of("shipmentId", sid));
        entityManager.flush();
        entityManager.clear();
        JsonNode view = ok("GET", "/api/trips/" + trip.path("id").asText(), t, null);
        assertEquals("Guest sender", view.path("luggage").get(0).path("shipments").get(0).path("senderName").asText());
        assertEquals(1, view.path("luggage").get(0).path("shipments").get(0).path("photoCount").asInt());
        assertEquals(trip.path("id"), ok("GET", "/api/shipments/" + sid, a, null).path("tripId"));
    }

    @Test
    void conversionReleasesMarketplaceCapacityRefundsDemoAndTransfersSelectedCarrier() throws Exception {
        String a = admin(), t = traveler();
        JsonNode guest = guestRequest(a);
        String requestPath = "/api/marketplace/requests/" + guest.path("id").asText();
        JsonNode trip = ok("POST", "/api/marketplace/trips", t, Map.of("origin", route, "destination", "Addis Ababa",
                "departureDate", LocalDate.now().plusDays(2).toString(), "capacityKg", 10,
                "allowedItems", List.of("DOCUMENTS"), "blockedItems", List.of()));
        JsonNode offer = ok("POST", requestPath + "/offers", t, Map.of("tripId", trip.path("id").asText(), "price", 25)).path("offers").get(0);
        ok("POST", requestPath + "/offers/" + offer.path("id").asText() + "/accept", a, null);
        ok("POST", requestPath + "/demo-escrow", a, null);
        JsonNode shipment = ok("POST", convertPath(guest), a, conversionInput());
        assertEquals(offer.path("traveler").path("id"), shipment.path("assignedTravelerId"));
        assertTrue(shipment.path("tripId").isNull());
        assertEquals("PENDING", shipment.path("paymentStatus").asText());
        entityManager.flush();
        entityManager.clear();
        var closed = ok("GET", requestPath, a, null);
        assertEquals("DEMO_REFUNDED", closed.path("escrowStatus").asText());
        assertEquals(0, requests.findById(UUID.fromString(guest.path("id").asText())).orElseThrow()
                .getAcceptedOffer().getTrip().getReservedKg().signum());
        call("POST", requestPath + "/offers", t, Map.of("tripId", trip.path("id").asText(), "price", 25))
                .andExpect(status().isBadRequest());
        assertTrue(ok("GET", "/api/shipments/" + shipment.path("id").asText(), t, null).path("senderId").isNull());
    }

    @Test
    void conversionDeclinesPendingOffersAndTransfersAcceptedAgent() throws Exception {
        String a = admin(), agent = login("agent@example.com", "Agent@123");
        JsonNode trip = ok("POST", "/api/marketplace/trips", agent, Map.of("origin", route, "destination", "Addis Ababa",
                "departureDate", LocalDate.now().plusDays(2).toString(), "capacityKg", 10,
                "allowedItems", List.of("DOCUMENTS"), "blockedItems", List.of()));
        for (boolean accept : List.of(false, true)) {
            JsonNode guest = guestRequest(a);
            String requestPath = "/api/marketplace/requests/" + guest.path("id").asText();
            JsonNode offer = ok("POST", requestPath + "/offers", agent, Map.of("tripId", trip.path("id").asText(), "price", 25)).path("offers").get(0);
            if (accept) ok("POST", requestPath + "/offers/" + offer.path("id").asText() + "/accept", a, null);
            JsonNode shipment = ok("POST", convertPath(guest), a, conversionInput());
            if (accept) assertEquals(offer.path("traveler").path("id"), shipment.path("assignedAgentId"));
            else {
                assertTrue(shipment.path("assignedAgentId").isNull());
                JsonNode closed = ok("GET", requestPath, a, null);
                assertEquals("DECLINED", closed.path("offers").get(0).path("status").asText());
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invalidConversionRollsBackMarketplaceClosureAndDoesNotCreateShipment() throws Exception {
        String a = admin();
        JsonNode guest = guestRequest(a);
        var invalid = conversionInput();
        invalid.put("pickupMethod", "PICKUP");
        call("POST", convertPath(guest), a, invalid).andExpect(status().isBadRequest());
        var request = ok("GET", "/api/marketplace/requests/" + guest.path("id").asText(), a, null);
        assertEquals("REQUESTED", request.path("status").asText());
        assertTrue(request.path("convertedShipmentId").isNull());
        invalid.put("pickupAddress", "123 Main Street");
        invalid.put("pickupRequestedDate", LocalDate.now().plusDays(1).toString());
        assertEquals("PICKUP_SCHEDULED", ok("POST", convertPath(guest), a, invalid).path("status").asText());
    }

    @Test
    void conversionRejectsAccountRequestsClosedRequestsAndMissingRecipient() throws Exception {
        String a = admin();
        JsonNode regular = ok("POST", "/api/marketplace/requests", sender(), requestInput());
        call("POST", convertPath(regular), a, conversionInput()).andExpect(status().isBadRequest());
        JsonNode guest = guestRequest(a);
        var bad = conversionInput();
        bad.remove("recipient");
        call("POST", convertPath(guest), a, bad).andExpect(status().isBadRequest());
        putOk("/api/marketplace/requests/" + guest.path("id").asText() + "/status", a, Map.of("status", "CANCELLED"));
        call("POST", convertPath(guest), a, conversionInput()).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousConversionsCreateOneShipment() throws Exception {
        String a = admin();
        JsonNode guest = guestRequest(a);
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            Callable<JsonNode> convert = () -> {
                assertTrue(gate.await(10, TimeUnit.SECONDS));
                return ok("POST", convertPath(guest), a, conversionInput());
            };
            var first = pool.submit(convert);
            var second = pool.submit(convert);
            gate.countDown();
            assertEquals(first.get(30, TimeUnit.SECONDS).path("id"), second.get(30, TimeUnit.SECONDS).path("id"));
            assertEquals(1, shipments.findAll().stream().filter(shipment -> shipment.getSourceRequest() != null
                    && shipment.getSourceRequest().getId().toString().equals(guest.path("id").asText())).count());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void onlyAdminCanCreateListOrRevokeLinksAndRawTokensAreNotStored() throws Exception {
        call("POST", adminBase, null, Map.of("label", "Guest")).andExpect(status().isUnauthorized());
        for (String token : List.of(sender(), traveler())) {
            call("POST", adminBase, token, Map.of("label", "Guest")).andExpect(status().isForbidden());
            call("GET", adminBase, token, null).andExpect(status().isForbidden());
        }
        String a = admin();
        JsonNode created = link(a);
        String token = created.path("token").asText();
        assertEquals(43, token.length());
        var stored = links.findById(UUID.fromString(created.path("link").path("id").asText())).orElseThrow();
        assertEquals(64, stored.getTokenHash().length());
        assertFalse(stored.getTokenHash().contains(token));
        assertEquals(Duration.ofDays(7), Duration.between(stored.getCreatedAt(), stored.getExpiresAt()));
        assertFalse(ok("GET", adminBase, a, null).toString().contains(token));
        call("POST", adminBase + "/" + stored.getId() + "/revoke", sender(), null).andExpect(status().isForbidden());
        call("POST", publicBase + "/check", null, Map.of("token", "a".repeat(43))).andExpect(status().isNotFound());
    }

    @Test
    void guestCanSubmitWithoutAccountAndOnlyAdminCanSeeContacts() throws Exception {
        String a = admin(), t = traveler(), s = sender();
        JsonNode created = link(a);
        String token = created.path("token").asText();
        assertEquals("OPEN", ok("POST", publicBase + "/check", null, Map.of("token", token)).path("status").asText());
        JsonNode receipt = ok("POST", publicBase + "/submit", null, submission(token));
        assertEquals("SUBMITTED", receipt.path("status").asText());
        assertTrue(receipt.path("message").asText().contains("We will contact you once we find a traveler"));
        assertFalse(receipt.toString().contains("guest@example.com"));
        JsonNode request = requestFor(created, a);
        assertTrue(request.path("guestRequest").asBoolean());
        assertTrue(request.path("senderId").isNull());
        assertEquals("guest@example.com", request.path("senderContact").path("email").asText());
        assertTrue(request.path("hasProductPhoto").asBoolean());
        String path = "/api/marketplace/requests/" + request.path("id").asText();
        JsonNode carrierView = ok("GET", path, t, null);
        assertTrue(carrierView.path("senderName").isNull());
        assertTrue(carrierView.path("senderContact").isNull());
        assertFalse(carrierView.toString().contains("guest@example.com"));
        assertFalse(carrierView.toString().contains("data:image"));
        assertTrue(ok("GET", path + "/product-photo", t, null).path("photoDataUrl").asText().startsWith("data:image/jpeg;base64,"));
        call("GET", path, s, null).andExpect(status().isForbidden());
        call("GET", path + "/product-photo", s, null).andExpect(status().isForbidden());
        call("GET", path + "/product-photo", null, null).andExpect(status().isUnauthorized());
        assertTrue(ok("GET", "/api/package-queue/notifications", a, null).toString().contains(request.path("id").asText()));
    }

    @Test
    void retriesReturnReceiptWithoutCreatingAnotherRequest() throws Exception {
        String a = admin();
        JsonNode created = link(a);
        String token = created.path("token").asText();
        Map<String, Object> input = submission(token);
        long before = requests.count();
        ok("POST", publicBase + "/submit", null, input);
        ok("POST", publicBase + "/submit", null, input);
        assertEquals(before + 1, requests.count());
        assertEquals("SUBMITTED", ok("POST", publicBase + "/check", null, Map.of("token", token)).path("status").asText());
        call("POST", adminBase + "/" + created.path("link").path("id").asText() + "/revoke", a, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredAndRevokedLinksCannotPost() throws Exception {
        String a = admin();
        JsonNode expired = link(a);
        var row = links.findById(UUID.fromString(expired.path("link").path("id").asText())).orElseThrow();
        row.setExpiresAt(Instant.now().minusSeconds(1));
        links.saveAndFlush(row);
        String token = expired.path("token").asText();
        assertEquals("EXPIRED", ok("POST", publicBase + "/check", null, Map.of("token", token)).path("status").asText());
        call("POST", publicBase + "/submit", null, submission(token)).andExpect(status().isBadRequest());
        JsonNode revoked = link(a);
        ok("POST", adminBase + "/" + revoked.path("link").path("id").asText() + "/revoke", a, null);
        token = revoked.path("token").asText();
        assertEquals("REVOKED", ok("POST", publicBase + "/check", null, Map.of("token", token)).path("status").asText());
        call("POST", publicBase + "/submit", null, submission(token)).andExpect(status().isBadRequest());
    }

    @Test
    void missingContactsPhotoInvalidDatesAndInvalidImagesDoNotConsumeLink() throws Exception {
        String token = link(admin()).path("token").asText();
        Map<String, Object> input = submission(token);
        for (String key : List.of("name", "phone", "email", "photoDataUrl")) {
            var invalid = new HashMap<>(input);
            invalid.remove(key);
            call("POST", publicBase + "/submit", null, invalid).andExpect(status().isBadRequest());
        }
        for (var invalid : List.of(Map.of("email", "not-email"), Map.of("phone", "no phone"),
                Map.of("photoDataUrl", "data:image/svg+xml;base64,PHN2Zz4="),
                Map.of("photoDataUrl", "data:image/png;base64,bm90LWFuLWltYWdl"),
                Map.of("photoDataUrl", "data:image/png;base64,not base64"))) {
            var bad = new HashMap<>(input);
            bad.putAll(invalid);
            call("POST", publicBase + "/submit", null, bad).andExpect(status().isBadRequest());
        }
        var dates = requestInput();
        dates.put("deadline", LocalDate.now().toString());
        var bad = new HashMap<>(input);
        bad.put("request", dates);
        call("POST", publicBase + "/submit", null, bad).andExpect(status().isBadRequest());
        assertEquals("OPEN", ok("POST", publicBase + "/check", null, Map.of("token", token)).path("status").asText());
        ok("POST", publicBase + "/submit", null, input);
    }

    @Test
    void adminHandlesGuestOffersAndFundingButNotRegularSenderOffers() throws Exception {
        String a = admin(), t = traveler(), s = sender();
        JsonNode created = link(a);
        ok("POST", publicBase + "/submit", null, submission(created.path("token").asText()));
        JsonNode guest = requestFor(created, a);
        String path = "/api/marketplace/requests/" + guest.path("id").asText();
        JsonNode trip = ok("POST", "/api/marketplace/trips", t, Map.of("origin", route, "destination", "Addis Ababa",
                "departureDate", LocalDate.now().plusDays(2).toString(), "expectedArrivalDate", LocalDate.now().plusDays(3).toString(),
                "capacityKg", 10, "allowedItems", List.of("DOCUMENTS"), "blockedItems", List.of()));
        JsonNode offered = ok("POST", path + "/offers", t, Map.of("tripId", trip.path("id").asText(), "price", 25));
        assertTrue(ok("GET", "/api/package-queue/notifications", a, null).toString().contains("contact the guest sender"));
        String offerId = offered.path("offers").get(0).path("id").asText();
        call("POST", path + "/offers/" + offerId + "/accept", s, null).andExpect(status().isForbidden());
        assertEquals("MATCHED", ok("POST", path + "/offers/" + offerId + "/accept", a, null).path("status").asText());
        call("GET", path + "/messages", t, null).andExpect(status().isForbidden());
        call("POST", path + "/messages", t, Map.of("body", "Hello")).andExpect(status().isForbidden());
        assertEquals("PAID_ESCROW", ok("POST", path + "/demo-escrow", a, null).path("status").asText());
        assertTrue(ok("GET", path, t, null).path("senderContact").isNull());
        for (String state : List.of("PICKED_UP", "IN_TRANSIT", "DELIVERED", "COMPLETED")) {
            var response = mvc.perform(put(path + "/status").header("Authorization", "Bearer " + a)
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("status", state))));
            response.andExpect(status().isOk());
        }
        JsonNode regular = ok("POST", "/api/marketplace/requests", s, requestInput());
        String regularPath = "/api/marketplace/requests/" + regular.path("id").asText();
        JsonNode regularOffer = ok("POST", regularPath + "/offers", t, Map.of("tripId", trip.path("id").asText(), "price", 25));
        call("POST", regularPath + "/offers/" + regularOffer.path("offers").get(0).path("id").asText() + "/accept", a, null)
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousSubmissionsConsumeLinkOnlyOnce() throws Exception {
        String a = admin();
        JsonNode created = link(a);
        Map<String, Object> input = submission(created.path("token").asText());
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            Callable<Integer> submit = () -> {
                assertTrue(gate.await(10, TimeUnit.SECONDS));
                return call("POST", publicBase + "/submit", null, input).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            gate.countDown();
            assertEquals(200, first.get(30, TimeUnit.SECONDS));
            assertEquals(200, second.get(30, TimeUnit.SECONDS));
            assertEquals(1, requests.findAllByOrderByCreatedAtDesc().stream().filter(r -> route.equals(r.getPickupCity())).count());
        } finally { pool.shutdownNow(); }
    }
}
