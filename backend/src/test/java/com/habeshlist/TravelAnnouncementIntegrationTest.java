package com.habeshlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshlist.repository.TravelAnnouncementRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TravelAnnouncementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired TravelAnnouncementRepository announcements;
    @Autowired EntityManager em;

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("token").asText();
    }

    private Map<String, Object> announcement(LocalDate departure) {
        return new HashMap<>(Map.of("origin", " IAD ", "destination", " Addis Ababa ",
                "departureDate", departure.toString(), "expectedArrivalDate", departure.plusDays(1).toString(),
                "notes", "Drop off packages before departure.", "totalCapacityKg", 46, "numberOfLuggage", 2));
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        return om.readTree(mvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode travel(String agent, double capacity) throws Exception {
        Map<String, Object> request = announcement(LocalDate.now().plusDays(10));
        request.put("totalCapacityKg", capacity);
        return postJson("/api/travel-announcements", agent, request);
    }

    private JsonNode shipment(String sender, String travelId, double weight) throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of(
                "recipient", Map.of("name", "Receiver", "phone", "12345678", "city", "Addis Ababa"),
                "destinationCountry", "Ethiopia", "destinationCity", "Addis Ababa",
                "declaredWeightKg", weight, "pickupMethod", "ALEXANDRIA_DROPOFF"));
        if (travelId != null) request.put("travelAnnouncementId", travelId);
        return postJson("/api/shipments", sender, request);
    }

    private String decisionPath(String travelId, JsonNode shipment) {
        return "/api/travel-announcements/" + travelId + "/requests/" + shipment.get("id").asText();
    }

    private void decide(String token, String travelId, JsonNode shipment, String status, int expected) throws Exception {
        mvc.perform(put(decisionPath(travelId, shipment)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("status", status))))
                .andExpect(status().is(expected));
    }

    private JsonNode capacity(String token, String id) throws Exception {
        JsonNode list = om.readTree(mvc.perform(get("/api/travel-announcements")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode announcement : list) {
            if (announcement.get("id").asText().equals(id)) return announcement;
        }
        throw new AssertionError("Missing travel announcement");
    }

    @Test
    void agentPublishesWithoutTravelerAndSenderSeesPersistedSafeAnnouncement() throws Exception {
        String agent = login("agent@example.com", "Agent@123");
        LocalDate departure = LocalDate.now().plusDays(10);
        Map<String, Object> request = announcement(departure);
        request.put("postedByName", "Forged author");
        JsonNode created = om.readTree(mvc.perform(post("/api/travel-announcements")
                        .header("Authorization", "Bearer " + agent).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postedByName").value("Beti Alemu"))
                .andExpect(jsonPath("$.origin").value("IAD"))
                .andExpect(jsonPath("$.destination").value("Addis Ababa"))
                .andExpect(jsonPath("$.departureDate").value(departure.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertEquals(1, announcements.count());

        String sender = login("sender@example.com", "Sender@123");
        mvc.perform(get("/api/travel-announcements").header("Authorization", "Bearer " + sender))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(created.get("id").asText()))
                .andExpect(jsonPath("$[0].postedByName").value("Beti Alemu"))
                .andExpect(jsonPath("$[0].notes").value("Drop off packages before departure."))
                .andExpect(jsonPath("$[0].travelerId").doesNotExist())
                .andExpect(jsonPath("$[0].luggage").doesNotExist())
                .andExpect(jsonPath("$[0].shipments").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].postedBy").doesNotExist());
        // Announcements must not grant access to operational manifests.
        mvc.perform(get("/api/trips").header("Authorization", "Bearer " + sender))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void onlyAgentsAndAdminsCanPublishAndAnonymousUsersCannotRead() throws Exception {
        String body = om.writeValueAsString(announcement(LocalDate.now().plusDays(5)));
        mvc.perform(get("/api/travel-announcements")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/travel-announcements").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        for (String[] credentials : new String[][]{
                {"sender@example.com", "Sender@123"}, {"traveler@example.com", "Traveler@123"}}) {
            String token = login(credentials[0], credentials[1]);
            mvc.perform(post("/api/travel-announcements").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        String admin = login("admin@habeshlist.com", "Admin@123");
        mvc.perform(post("/api/travel-announcements").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.postedByName").value("Admin User"));
    }

    @Test
    void validatesDatesAndFieldsAndListsInDepartureOrder() throws Exception {
        String agent = login("agent@example.com", "Agent@123");
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                announcement(LocalDate.now().minusDays(1)),
                new HashMap<>(Map.of("origin", " ", "destination", "ADD", "departureDate", LocalDate.now().toString())),
                new HashMap<>(Map.of("origin", "IAD", "destination", "ADD")),
                new HashMap<>(Map.of("origin", "x".repeat(101), "destination", "ADD", "departureDate", LocalDate.now().toString()))
        )) {
            mvc.perform(post("/api/travel-announcements").header("Authorization", "Bearer " + agent)
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
        Map<String, Object> backwards = announcement(LocalDate.now().plusDays(5));
        backwards.put("expectedArrivalDate", LocalDate.now().toString());
        mvc.perform(post("/api/travel-announcements").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(backwards)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Arrival date cannot be before departure date"));
        assertEquals(0, announcements.count());
        for (LocalDate departure : new LocalDate[]{LocalDate.now().plusDays(30), LocalDate.now()}) {
            Map<String, Object> valid = announcement(departure);
            valid.remove("expectedArrivalDate");
            valid.remove("notes");
            mvc.perform(post("/api/travel-announcements").header("Authorization", "Bearer " + agent)
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(valid)))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/travel-announcements").header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].departureDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$[1].departureDate").value(LocalDate.now().plusDays(30).toString()));
    }

    @Test
    void pendingRequestsDoNotReserveAndApprovalChecksExactRemainingWeightWithoutDoubleDeduction() throws Exception {
        String agent = login("agent@example.com", "Agent@123");
        String sender = login("sender@example.com", "Sender@123");
        String id = travel(agent, 10.25).get("id").asText();
        JsonNode first = shipment(sender, id, 7.15);
        JsonNode second = shipment(sender, id, 3.10);
        JsonNode excess = shipment(sender, id, 0.10);
        assertEquals("PENDING", first.get("travelBookingStatus").asText());
        assertEquals(10.25, capacity(sender, id).get("availableCapacityKg").asDouble());
        decide(agent, id, first, "APPROVED", 200);
        decide(agent, id, first, "APPROVED", 200);
        assertEquals(3.10, capacity(sender, id).get("availableCapacityKg").asDouble());
        decide(agent, id, second, "APPROVED", 200);
        assertEquals(0, capacity(sender, id).get("availableCapacityKg").asDouble());
        decide(agent, id, excess, "APPROVED", 400);
        decide(agent, id, excess, "REJECTED", 200);
        assertEquals(10.25, capacity(sender, id).get("approvedWeightKg").asDouble());
        mvc.perform(get("/api/shipments/" + first.get("id").asText()).header("Authorization", "Bearer " + sender))
                .andExpect(status().isOk()).andExpect(jsonPath("$.travelBookingStatus").value("APPROVED"));
        mvc.perform(get("/api/shipments/" + first.get("id").asText() + "/tracking")
                        .header("Authorization", "Bearer " + sender))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType == 'TRAVEL_REQUEST_APPROVED')]", hasSize(1)));
    }

    @Test
    void onlyPostingAgentOrAdminCanDecideOrChangeCapacityAndRequestsStayPrivate() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String agent = login("agent@example.com", "Agent@123");
        String sender = login("sender@example.com", "Sender@123");
        String traveler = login("traveler@example.com", "Traveler@123");
        String id = travel(admin, 10).get("id").asText();
        JsonNode request = shipment(sender, id, 4);
        for (String token : List.of(agent, sender, traveler)) {
            decide(token, id, request, "APPROVED", 403);
            mvc.perform(put("/api/travel-announcements/" + id + "/capacity")
                            .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"totalCapacityKg\":100}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/travel-announcements/requests").header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        for (String token : List.of(sender, traveler)) {
            mvc.perform(get("/api/travel-announcements/requests").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/travel-announcements/requests").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        decide(admin, id, request, "APPROVED", 200);
        mvc.perform(put("/api/travel-announcements/" + id + "/capacity")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacityKg\":3}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/travel-announcements/" + id + "/capacity")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacityKg\":15,\"numberOfLuggage\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableCapacityKg").value(11));
        // Approving a request does not expose its full shipment to an unrelated agent.
        mvc.perform(get("/api/shipments/" + request.get("id").asText()).header("Authorization", "Bearer " + agent))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancellationReleasesSpaceAndOldOrPastPostsCannotAcceptRequests() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String agent = login("agent@example.com", "Agent@123");
        String sender = login("sender@example.com", "Sender@123");
        String id = travel(agent, 10).get("id").asText();
        JsonNode request = shipment(sender, id, 6);
        decide(agent, id, request, "APPROVED", 200);
        mvc.perform(put("/api/shipments/" + request.get("id").asText() + "/status")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.travelBookingStatus").value("CANCELLED"));
        assertEquals(10, capacity(sender, id).get("availableCapacityKg").asDouble());
        decide(agent, id, request, "APPROVED", 400);
        var announcement = announcements.findById(UUID.fromString(id)).orElseThrow();
        announcement.setTotalCapacityKg(null);
        em.flush();
        Map<String, Object> payload = Map.of("travelAnnouncementId", id,
                "recipient", Map.of("name", "Receiver", "phone", "12345678", "city", "Addis Ababa"),
                "destinationCountry", "Ethiopia", "destinationCity", "Addis Ababa",
                "declaredWeightKg", 1, "pickupMethod", "ALEXANDRIA_DROPOFF");
        mvc.perform(post("/api/shipments").header("Authorization", "Bearer " + sender)
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        announcement.setTotalCapacityKg(java.math.BigDecimal.TEN);
        announcement.setDepartureDate(LocalDate.now().minusDays(1));
        em.flush();
        mvc.perform(post("/api/shipments").header("Authorization", "Bearer " + sender)
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        assertTrue(shipment(sender, null, 1).get("travelBookingStatus").isNull());
    }

    @Test
    void validatesCapacityAndWeightPrecisionAndDeletionReleasesApprovedSpace() throws Exception {
        String agent = login("agent@example.com", "Agent@123");
        String admin = login("admin@habeshlist.com", "Admin@123");
        String sender = login("sender@example.com", "Sender@123");
        for (Object invalid : List.of(0, -1, 1.001, 100000000)) {
            var payload = announcement(LocalDate.now().plusDays(10));
            payload.put("totalCapacityKg", invalid);
            mvc.perform(post("/api/travel-announcements").header("Authorization", "Bearer " + agent)
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
        String id = travel(agent, 10).get("id").asText();
        Map<String, Object> badWeight = Map.of("travelAnnouncementId", id,
                "recipient", Map.of("name", "Receiver", "phone", "12345678", "city", "Addis Ababa"),
                "destinationCountry", "Ethiopia", "destinationCity", "Addis Ababa",
                "declaredWeightKg", 1.001, "pickupMethod", "ALEXANDRIA_DROPOFF");
        mvc.perform(post("/api/shipments").header("Authorization", "Bearer " + sender)
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(badWeight)))
                .andExpect(status().isBadRequest());
        JsonNode request = shipment(sender, id, 4);
        decide(agent, id, request, "APPROVED", 200);
        UUID shipmentId = UUID.fromString(request.get("id").asText());
        // PostgreSQL cascades tracking-event deletes; remove them explicitly in the generated H2 schema.
        eventRepository.deleteAll(eventRepository.findAllByShipmentIdOrderByCreatedAtAsc(shipmentId));
        eventRepository.flush();
        mvc.perform(delete("/api/shipments/" + shipmentId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertEquals(10, capacity(sender, id).get("availableCapacityKg").asDouble());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousApprovalsCannotOverbook() throws Exception {
        String agent = login("agent@example.com", "Agent@123");
        String sender = login("sender@example.com", "Sender@123");
        String id = travel(agent, 10).get("id").asText();
        JsonNode first = shipment(sender, id, 6);
        JsonNode second = shipment(sender, id, 6);
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var results = List.of(first, second).stream().map(request -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Approval start timed out");
                return mvc.perform(put(decisionPath(id, request)).header("Authorization", "Bearer " + agent)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                        .andReturn().getResponse().getStatus();
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(400));
            assertEquals(4, capacity(sender, id).get("availableCapacityKg").asDouble());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            // Restore the shared test database, including tracking events created by approvals.
            for (JsonNode request : List.of(first, second)) {
                UUID shipmentId = UUID.fromString(request.get("id").asText());
                cleanupShipment(shipmentId);
            }
            announcements.deleteById(UUID.fromString(id));
        }
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.habeshlist.repository.ShipmentRepository shipmentRepository;
    @Autowired com.habeshlist.repository.TrackingEventRepository eventRepository;

    private void cleanupShipment(UUID id) {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            eventRepository.deleteAll(eventRepository.findAllByShipmentIdOrderByCreatedAtAsc(id));
            eventRepository.flush();
            shipmentRepository.deleteById(id);
        });
    }
}
