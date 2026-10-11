package com.habeshlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshlist.repository.TrackingEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsolidationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired TrackingEventRepository events;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void multipleRecipientsAndFivePicturesPersistThroughConsolidation() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String image = "data:image/png;base64,YQ==";
        List<String> photos = List.of(image, image, image, image, image);
        JsonNode shipment = postJson("/api/shipments", admin, Map.of(
                "recipient", Map.of("name", "First", "phone", "111", "city", "Addis Ababa"),
                "additionalRecipients", List.of(
                        Map.of("name", "Second", "phone", "222", "city", "Addis Ababa"),
                        Map.of("name", "Third", "phone", "333", "city", "Addis Ababa")),
                "photoDataUrls", photos, "destinationCountry", "Ethiopia", "destinationCity", "Addis Ababa",
                "declaredWeightKg", 2, "pickupMethod", "ALEXANDRIA_DROPOFF"));
        String sid = shipment.get("id").asText();
        assertEquals(5, shipment.get("photoCount").asInt());
        assertEquals(3, shipment.get("recipients").size());
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/shipments/" + sid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipient.name", is("First")))
                .andExpect(jsonPath("$.recipients[1].name", is("Second")))
                .andExpect(jsonPath("$.recipients[2].phone", is("333")))
                .andExpect(jsonPath("$.photoCount", is(5))).andExpect(jsonPath("$.hasPhoto", is(true)));
        mvc.perform(get("/api/shipments/" + sid + "/photos").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photos", hasSize(5)));
        JsonNode trip = createTrip(admin, 2, 1);
        String lid = trip.get("luggage").get(0).get("id").asText();
        postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", sid));
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/trips/" + trip.get("id").asText()).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedWeightKg", is(2.0)))
                .andExpect(jsonPath("$.luggage[0].shipments[0].recipients", hasSize(3)))
                .andExpect(jsonPath("$.luggage[0].shipments[0].photoCount", is(5)));
    }

    @Test
    void galleryEditsRespectPermissionsLimitsAndPreservePackageState() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String sender = login("sender@example.com", "Sender@123");
        String other = login("sender2@example.com", "Sender@123");
        String agent = login("agent@example.com", "Agent@123");
        String traveler = login("traveler@example.com", "Traveler@123");
        JsonNode shipment = createPackage(sender, 2);
        String sid = shipment.get("id").asText();
        String path = "/api/shipments/" + sid + "/photos";
        String image = "data:image/png;base64,YQ==";
        String replacement = "data:image/jpeg;base64,Yg==";
        putJson(path, sender, Map.of("photoDataUrls", List.of(image, replacement)));
        for (String token : List.of(other, agent, traveler)) {
            mvc.perform(put(path).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of("photoDataUrls", List.of(image)))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(path).header("Authorization", "Bearer " + other)).andExpect(status().isForbidden());
        for (Object invalid : List.of(List.of(image, image, image, image, image, image),
                List.of(""), List.of("not-an-image"), List.of("data:image/png;" + "x".repeat(3_000_000)))) {
            mvc.perform(put(path).header("Authorization", "Bearer " + sender)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of("photoDataUrls", invalid))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put(path).header("Authorization", "Bearer " + sender)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get(path).header("Authorization", "Bearer " + sender))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photos", hasSize(2)))
                .andExpect(jsonPath("$.photos[1]", is(replacement)));
        putJson("/api/shipments/" + sid + "/photo", admin, Map.of("photoDataUrl", replacement));
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get(path).header("Authorization", "Bearer " + sender))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photos", hasSize(2)))
                .andExpect(jsonPath("$.photos[0]", is(replacement)))
                .andExpect(jsonPath("$.photos[1]", is(replacement)));
        putJson(path, admin, Map.of("photoDataUrls", List.of(image)));
        JsonNode cleared = putJson(path, sender, Map.of("photoDataUrls", List.of()));
        assertFalse(cleared.get("hasPhoto").asBoolean());
        assertEquals(0, cleared.get("photoCount").asInt());
        assertEquals(shipment.get("status"), cleared.get("status"));
        assertEquals(shipment.get("declaredWeightKg").asDouble(), cleared.get("declaredWeightKg").asDouble());
        assertEquals(shipment.get("recipient"), cleared.get("recipient"));
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get(path).header("Authorization", "Bearer " + sender))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photos", hasSize(0)));
        mvc.perform(get("/api/shipments/" + sid + "/photo").header("Authorization", "Bearer " + sender))
                .andExpect(status().isNotFound());
    }

    @Test
    void creationRejectsInvalidCollectionsAndGuestCheckoutIsDisabled() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        Map<String, Object> payload = new java.util.HashMap<>(Map.of(
                "recipient", Map.of("name", "First", "phone", "111", "city", "Addis Ababa"),
                "destinationCountry", "Ethiopia", "destinationCity", "Addis Ababa",
                "declaredWeightKg", 1, "pickupMethod", "ALEXANDRIA_DROPOFF"));
        String image = "data:image/png;base64,YQ==";
        payload.put("photoDataUrls", List.of(image, image, image, image, image, image));
        mvc.perform(post("/api/shipments").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        payload.put("photoDataUrls", List.of(image));
        payload.put("additionalRecipients", List.of(Map.of("name", "", "phone", "222", "city", "Addis Ababa")));
        mvc.perform(post("/api/shipments").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        payload.put("additionalRecipients", List.of(Map.of("name", "Second", "phone", "222", "city", "Addis Ababa")));
        payload.put("photoDataUrl", image);
        mvc.perform(post("/api/shipments").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        payload.remove("photoDataUrl");
        mvc.perform(post("/api/shipments/guest").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(payload))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/shipments/guest").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    private String login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "", Map.of("email", email, "password", password)).get("token").asText();
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        return om.readTree(mvc.perform(post(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode putJson(String path, String token, Object body) throws Exception {
        return om.readTree(mvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode createTrip(String token, int capacity, int luggageCount) throws Exception {
        JsonNode travelers = om.readTree(mvc.perform(get("/api/admin/users?role=TRAVELER")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return postJson("/api/trips", token, Map.of(
                "travelerId", travelers.get(0).get("id").asText(),
                "departureAirport", "IAD", "arrivalAirport", "ADD", "departureDate", "2026-12-01",
                "totalLuggageCapacity", capacity, "numberOfLuggage", luggageCount));
    }

    private JsonNode createPackage(String token, int weight) throws Exception {
        return postJson("/api/shipments", token, Map.of(
                "recipient", Map.of("name", "Recipient", "phone", "123456789", "city", "Addis Ababa"),
                "destinationCountry", "Ethiopia", "destinationCity", "Addis Ababa",
                "declaredWeightKg", weight, "pickupMethod", "ALEXANDRIA_DROPOFF"));
    }

    @Test
    void receivingAgentCanReadLuggagePicturesButCannotAssignPackagesToTrips() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String agent = login("agent@example.com", "Agent@123");
        String agentId = om.readTree(mvc.perform(get("/api/me").header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String sid = createPackage(admin, 2).get("id").asText();
        String image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aQ5sAAAAASUVORK5CYII=";
        putJson("/api/shipments/" + sid + "/photo", admin, Map.of("photoDataUrl", image));
        JsonNode trip = createTrip(admin, 5, 1);
        String tid = trip.get("id").asText();
        String lid = trip.get("luggage").get(0).get("id").asText();
        postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", sid));
        mvc.perform(get("/api/shipments/" + sid + "/photo").header("Authorization", "Bearer " + agent))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/shipments/" + sid + "/photos").header("Authorization", "Bearer " + agent))
                .andExpect(status().isForbidden());
        putJson("/api/luggage/" + lid + "/agent", admin, Map.of("agentId", agentId));
        mvc.perform(get("/api/trips/" + tid).header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.luggage[0].shipments[0].hasPhoto", is(true)));
        mvc.perform(get("/api/shipments/" + sid + "/photo").header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photo", is(image)));
        mvc.perform(get("/api/shipments/" + sid + "/photos").header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photos[0]", is(image)));
        String direct = createPackage(admin, 3).get("id").asText();
        mvc.perform(put("/api/shipments/" + direct + "/trip").header("Authorization", "Bearer " + agent)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("tripId", tid))))
                .andExpect(status().isForbidden());
        putJson("/api/shipments/" + direct + "/trip", admin, Map.of("tripId", tid));
        mvc.perform(get("/api/trips/" + tid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedWeightKg", is(5)))
                .andExpect(jsonPath("$.directShipments[0].id", is(direct)));
    }

    @Test
    void agentIncomingPackagesIncludeRecipientContactAndDescription() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String agent = login("agent@example.com", "Agent@123");
        String agentId = om.readTree(mvc.perform(get("/api/me").header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();
        JsonNode shipment = postJson("/api/shipments", admin, Map.of(
                "recipient", Map.of("name", "Delivery Recipient", "phone", "+251911123456", "city", "Addis Ababa"),
                "description", "Books and clothes",
                "destinationCountry", "Ethiopia", "destinationCity", "Addis Ababa",
                "declaredWeightKg", 2, "pickupMethod", "ALEXANDRIA_DROPOFF"));
        String sid = shipment.get("id").asText();
        putJson("/api/shipments/" + sid + "/agent", admin, Map.of("agentId", agentId));
        JsonNode trip = createTrip(admin, 10, 1);
        String tid = trip.get("id").asText();
        String lid = trip.get("luggage").get(0).get("id").asText();
        postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", sid));
        mvc.perform(get("/api/trips/" + tid).header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.luggage[0].shipments[0].recipientName", is("Delivery Recipient")))
                .andExpect(jsonPath("$.luggage[0].shipments[0].recipientPhone", is("+251911123456")))
                .andExpect(jsonPath("$.luggage[0].shipments[0].description", is("Books and clothes")));
        mvc.perform(delete("/api/luggage/" + lid + "/shipments/" + sid)
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        putJson("/api/shipments/" + sid + "/trip", admin, Map.of("tripId", tid));
        mvc.perform(get("/api/trips/" + tid).header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directShipments[0].recipientName", is("Delivery Recipient")))
                .andExpect(jsonPath("$.directShipments[0].recipientPhone", is("+251911123456")))
                .andExpect(jsonPath("$.directShipments[0].description", is("Books and clothes")));
    }

    @Test
    void packagePictureCanBeUpdatedByAdminOrOwningSenderOnly() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String sender = login("sender@example.com", "Sender@123");
        JsonNode shipment = createPackage(sender, 2);
        String sid = shipment.get("id").asText();
        String lid = postJson("/api/luggage", admin, Map.of("capacityKg", 5)).get("id").asText();
        postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", sid));
        String firstPhoto = "data:image/png;base64,aGVsbG8=";
        JsonNode updated = putJson("/api/shipments/" + sid + "/photo", sender, Map.of("photoDataUrl", firstPhoto));
        assertTrue(updated.get("hasPhoto").asBoolean());
        assertEquals(lid, updated.get("luggageId").asText());
        assertEquals("ASSIGNED_TO_LUGGAGE", updated.get("status").asText());
        assertEquals(2, updated.get("declaredWeightKg").asInt());
        mvc.perform(get("/api/shipments/" + sid + "/photo").header("Authorization", "Bearer " + sender))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photo", is(firstPhoto)));
        String replacement = "data:image/jpeg;base64,dXBkYXRlZA==";
        putJson("/api/shipments/" + sid + "/photo", admin, Map.of("photoDataUrl", replacement));
        mvc.perform(get("/api/shipments/" + sid + "/photo").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photo", is(replacement)));
        mvc.perform(get("/api/luggage/" + lid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.shipments[0].hasPhoto", is(true)))
                .andExpect(jsonPath("$.assignedWeightKg", is(2)));
        for (String email : List.of("sender2@example.com", "agent@example.com", "traveler@example.com")) {
            String password = email.startsWith("sender") ? "Sender@123" : email.startsWith("agent") ? "Agent@123" : "Traveler@123";
            String token = login(email, password);
            mvc.perform(put("/api/shipments/" + sid + "/photo").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("photoDataUrl", firstPhoto))))
                    .andExpect(status().isForbidden());
        }
        for (String invalid : List.of("", "not-an-image", "data:image/png;base64," + "a".repeat(3_000_000))) {
            mvc.perform(put("/api/shipments/" + sid + "/photo").header("Authorization", "Bearer " + sender)
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("photoDataUrl", invalid))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/shipments/" + sid + "/photo").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photo", is(replacement)));
    }

    @Test
    void batchPackingAllowsExactCapacityAndRollsBackOverweightSelections() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String lid = postJson("/api/luggage", admin, Map.of("capacityKg", 5)).get("id").asText();
        String first = createPackage(admin, 2).get("id").asText();
        String second = createPackage(admin, 3).get("id").asText();
        String extra = createPackage(admin, 1).get("id").asText();
        mvc.perform(post("/api/luggage/" + lid + "/shipments/batch").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("shipmentIds", List.of(first, second, extra)))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/luggage/" + lid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedWeightKg", is(0)))
                .andExpect(jsonPath("$.shipments", hasSize(0)));
        for (String sid : List.of(first, second, extra)) {
            mvc.perform(get("/api/shipments/" + sid).header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.luggageId", nullValue()));
        }
        JsonNode packed = postJson("/api/luggage/" + lid + "/shipments/batch", admin,
                Map.of("shipmentIds", List.of(first, second, first)));
        assertEquals(5, packed.get("assignedWeightKg").asInt());
        assertEquals(0, packed.get("availableCapacityKg").asInt());
        assertEquals(2, packed.get("shipments").size());
        mvc.perform(post("/api/luggage/" + lid + "/shipments/batch").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("shipmentIds", List.of(extra)))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/luggage/" + lid + "/shipments/batch").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"shipmentIds\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/luggage/" + lid + "/shipments/" + first)
                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedWeightKg", is(3)))
                .andExpect(jsonPath("$.availableCapacityKg", is(2)));
    }

    @Test
    void consolidationIncludesSenderNameAndExistingPhotoMetadata() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String sender = login("sender@example.com", "Sender@123");
        String image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aQ5sAAAAASUVORK5CYII=";
        JsonNode shipment = postJson("/api/shipments", sender, Map.of(
                "recipient", Map.of("name", "Recipient", "phone", "123456789", "city", "Addis Ababa"),
                "destinationCountry", "Ethiopia", "destinationCity", "Addis Ababa",
                "declaredWeightKg", 1, "pickupMethod", "ALEXANDRIA_DROPOFF", "photoDataUrl", image));
        String sid = shipment.get("id").asText();
        String senderName = shipment.get("senderName").asText();
        assertFalse(senderName.isBlank());
        String lid = postJson("/api/luggage", admin, Map.of("capacityKg", 5)).get("id").asText();
        JsonNode packed = postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", sid));
        assertEquals(senderName, packed.get("shipments").get(0).get("senderName").asText());
        assertTrue(packed.get("shipments").get(0).get("hasPhoto").asBoolean());
        String withoutPhoto = createPackage(admin, 1).get("id").asText();
        postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", withoutPhoto));
        mvc.perform(get("/api/luggage/" + lid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipments[?(@.id == '" + sid + "')].senderName", hasItem(senderName)))
                .andExpect(jsonPath("$.shipments[?(@.id == '" + withoutPhoto + "')].hasPhoto", hasItem(false)));
        mvc.perform(get("/api/shipments/" + sid + "/photo").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photo", is(image)));
        String tid = createTrip(admin, 5, 0).get("id").asText();
        putJson("/api/luggage/" + lid + "/trip", admin, Map.of("tripId", tid));
        mvc.perform(get("/api/trips/" + tid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.luggage[0].shipments[?(@.id == '" + sid + "')].senderName", hasItem(senderName)));
        String unrelated = login("sender2@example.com", "Sender@123");
        mvc.perform(get("/api/shipments/" + sid + "/photo").header("Authorization", "Bearer " + unrelated))
                .andExpect(status().isForbidden());
    }

    @Test
    void consolidateBeforeTripThenSupportDirectPackagesAndPropagateTravel() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        JsonNode luggage = postJson("/api/luggage", admin, Map.of("capacityKg", 5));
        String lid = luggage.get("id").asText();
        assertTrue(luggage.get("tripId").isNull());
        assertTrue(luggage.get("tripNumber").isNull());
        String first = createPackage(admin, 2).get("id").asText();
        String second = createPackage(admin, 3).get("id").asText();
        String direct = createPackage(admin, 1).get("id").asText();
        postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", first));
        JsonNode packed = postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", second));
        assertEquals(5, packed.get("assignedWeightKg").asInt());
        assertEquals("PACKING", packed.get("status").asText());
        assertEquals(2, packed.get("shipments").size());
        JsonNode duplicate = postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", second));
        assertEquals(5, duplicate.get("assignedWeightKg").asInt());
        assertEquals(2, duplicate.get("shipments").size());
        mvc.perform(post("/api/luggage/" + lid + "/shipments").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("shipmentId", direct))))
                .andExpect(status().isBadRequest());

        String tooSmall = createTrip(admin, 4, 0).get("id").asText();
        mvc.perform(put("/api/luggage/" + lid + "/trip").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("tripId", tooSmall))))
                .andExpect(status().isBadRequest());
        String tid = createTrip(admin, 6, 0).get("id").asText();
        putJson("/api/luggage/" + lid + "/trip", admin, Map.of("tripId", tid));
        JsonNode assigned = putJson("/api/shipments/" + direct + "/trip", admin, Map.of("tripId", tid));
        assertEquals("ASSIGNED_TO_TRIP", assigned.get("status").asText());
        assertEquals(tid, assigned.get("tripId").asText());
        assertTrue(assigned.get("luggageId").isNull());
        mvc.perform(get("/api/trips/" + tid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedWeightKg", is(6)))
                .andExpect(jsonPath("$.availableCapacityKg", is(0)))
                .andExpect(jsonPath("$.luggage", hasSize(1)))
                .andExpect(jsonPath("$.directShipments", hasSize(1)));
        String overflow = createPackage(admin, 1).get("id").asText();
        mvc.perform(put("/api/shipments/" + overflow + "/trip").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("tripId", tid))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/luggage/" + lid + "/shipments").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("shipmentId", direct))))
                .andExpect(status().isBadRequest());

        String traveler = login("traveler@example.com", "Traveler@123");
        mvc.perform(get("/api/shipments/" + direct).header("Authorization", "Bearer " + traveler))
                .andExpect(status().isOk());
        mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + traveler))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(direct)));
        assertTrue(events.findAllByShipmentIdOrderByCreatedAtAsc(UUID.fromString(direct)).stream()
                .anyMatch(e -> e.getEventType().equals("ASSIGNED_TO_TRIP") && e.getTrip().getId().toString().equals(tid)));
        putJson("/api/trips/" + tid + "/status", admin, Map.of("status", "DEPARTED"));
        for (String sid : new String[]{first, second, direct}) {
            mvc.perform(get("/api/shipments/" + sid).header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("DEPARTED_US")));
        }
        mvc.perform(delete("/api/luggage/" + lid + "/shipments/" + first)
                .header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());
        putJson("/api/trips/" + tid + "/status", admin, Map.of("status", "ARRIVED"));
        mvc.perform(get("/api/shipments/" + direct).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("ARRIVED_ETHIOPIA")));
    }

    @Test
    void sealedLuggageMustBeReopenedAndUnassignedLuggageCannotBeDispatched() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String lid = postJson("/api/luggage", admin, Map.of("capacityKg", 5)).get("id").asText();
        String sid = createPackage(admin, 2).get("id").asText();
        putJson("/api/luggage/" + lid + "/status", admin, Map.of("status", "SEALED"));
        mvc.perform(post("/api/luggage/" + lid + "/shipments").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("shipmentId", sid))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/luggage/" + lid + "/status").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isBadRequest());
        putJson("/api/luggage/" + lid + "/status", admin, Map.of("status", "PACKING"));
        postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", sid));
        mvc.perform(delete("/api/luggage/" + lid + "/shipments/" + sid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedWeightKg", is(0)));
        String traveler = login("traveler@example.com", "Traveler@123");
        mvc.perform(get("/api/luggage/" + lid).header("Authorization", "Bearer " + traveler))
                .andExpect(status().isForbidden());
    }

    @Test
    void directAssignmentCanBeRemovedAndIsAdminOnly() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String tid = createTrip(admin, 5, 0).get("id").asText();
        String sid = createPackage(admin, 2).get("id").asText();
        String sender = login("sender@example.com", "Sender@123");
        mvc.perform(put("/api/shipments/" + sid + "/trip").header("Authorization", "Bearer " + sender)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("tripId", tid))))
                .andExpect(status().isForbidden());
        putJson("/api/shipments/" + sid + "/trip", admin, Map.of("tripId", tid));
        JsonNode removed = putJson("/api/shipments/" + sid + "/trip", admin, om.readTree("{\"tripId\":null}"));
        assertTrue(removed.get("tripId").isNull());
        assertEquals("READY_FOR_CONSOLIDATION", removed.get("status").asText());
        mvc.perform(get("/api/trips/" + tid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedWeightKg", is(0)));
        String lid = postJson("/api/luggage", admin, Map.of("capacityKg", 5)).get("id").asText();
        postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", sid));
        putJson("/api/luggage/" + lid + "/trip", admin, Map.of("tripId", tid));
        JsonNode detached = putJson("/api/luggage/" + lid + "/trip", admin, om.readTree("{\"tripId\":null}"));
        assertTrue(detached.get("tripId").isNull());
        assertEquals(1, detached.get("shipments").size());
        assertEquals(2, detached.get("assignedWeightKg").asInt());
        mvc.perform(get("/api/trips/" + tid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.luggage", hasSize(0)))
                .andExpect(jsonPath("$.numberOfLuggage", is(0)))
                .andExpect(jsonPath("$.assignedWeightKg", is(0)))
                .andExpect(jsonPath("$.availableCapacityKg", is(5)));
        mvc.perform(get("/api/shipments/" + sid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.luggageId", is(lid)))
                .andExpect(jsonPath("$.tripId", nullValue()))
                .andExpect(jsonPath("$.status", is("ASSIGNED_TO_LUGGAGE")));
        assertTrue(events.findAllByShipmentIdOrderByCreatedAtAsc(UUID.fromString(sid)).stream()
                .anyMatch(e -> e.getEventType().equals("LUGGAGE_REMOVED_FROM_TRIP")
                        && e.getTrip().getId().toString().equals(tid)));
        putJson("/api/luggage/" + lid + "/trip", admin, Map.of("tripId", tid));
        putJson("/api/trips/" + tid + "/status", admin, Map.of("status", "DEPARTED"));
        mvc.perform(put("/api/luggage/" + lid + "/trip").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"tripId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void autoAssignmentSkipsDirectAndCancelledPackagesAndRespectsTotalTripCapacity() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        JsonNode trip = createTrip(admin, 5, 1);
        String tid = trip.get("id").asText();
        String sid = createPackage(admin, 4).get("id").asText();
        putJson("/api/shipments/" + sid + "/trip", admin, Map.of("tripId", tid));
        String cancelled = createPackage(admin, 1).get("id").asText();
        putJson("/api/shipments/" + cancelled + "/status", admin, Map.of("status", "CANCELLED"));
        String eligible = createPackage(admin, 1).get("id").asText();
        JsonNode result = postJson("/api/luggage/trips/" + tid + "/auto-assign", admin, Map.of());
        assertEquals(1, result.get("assigned").asInt());
        mvc.perform(get("/api/shipments/" + sid).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.luggageId", nullValue()));
        mvc.perform(get("/api/shipments/" + cancelled).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.luggageId", nullValue()));
        mvc.perform(get("/api/shipments/" + eligible).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.luggageId", notNullValue()));
    }
}
