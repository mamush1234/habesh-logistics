package com.habeshlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshlist.repository.ScanAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ManifestAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired ScanAuditRepository audits;

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

    private JsonNode getJson(String path, String token) throws Exception {
        return om.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "", Map.of("email", email, "password", password)).get("token").asText();
    }

    private JsonNode trip(String admin, String travelerId) throws Exception {
        return postJson("/api/trips", admin, Map.of("travelerId", travelerId, "departureAirport", "IAD",
                "arrivalAirport", "ADD", "departureDate", "2026-12-01",
                "totalLuggageCapacity", 20, "numberOfLuggage", 2, "notes", "Private administrator note"));
    }

    private JsonNode shipment(String admin, String name) throws Exception {
        return postJson("/api/shipments", admin, Map.of("recipient", Map.of("name", name,
                "phone", "12345678", "city", "Addis Ababa", "address", name + " address", "notes", name + " notes"),
                "destinationCountry", "Ethiopia", "destinationCity", "Addis Ababa",
                "description", name + " description", "declaredWeightKg", 2, "pickupMethod", "ALEXANDRIA_DROPOFF"));
    }

    @Test
    void agentsSeeOnlyAuthorizedContentsInListDetailLuggageAndScans() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String agent = login("agent@example.com", "Agent@123");
        String agentId = getJson("/api/me", agent).get("id").asText();
        String travelerId = getJson("/api/admin/users?role=TRAVELER", admin).get(0).get("id").asText();
        JsonNode trip = trip(admin, travelerId);
        String tid = trip.get("id").asText();
        String firstLuggage = trip.get("luggage").get(0).get("id").asText();
        String secondLuggage = trip.get("luggage").get(1).get("id").asText();
        String secondCode = trip.get("luggage").get(1).get("luggageNumber").asText();
        JsonNode allowed = shipment(admin, "Authorized recipient");
        JsonNode hidden = shipment(admin, "Hidden recipient");
        JsonNode other = shipment(admin, "Other luggage recipient");
        JsonNode direct = shipment(admin, "Hidden direct recipient");
        postJson("/api/luggage/" + firstLuggage + "/shipments", admin, Map.of("shipmentId", allowed.get("id").asText()));
        postJson("/api/luggage/" + firstLuggage + "/shipments", admin, Map.of("shipmentId", hidden.get("id").asText()));
        postJson("/api/luggage/" + secondLuggage + "/shipments", admin, Map.of("shipmentId", other.get("id").asText()));
        putJson("/api/shipments/" + direct.get("id").asText() + "/trip", admin, Map.of("tripId", tid));
        mvc.perform(get("/api/trips/" + tid).header("Authorization", "Bearer " + agent)).andExpect(status().isForbidden());
        putJson("/api/shipments/" + allowed.get("id").asText() + "/agent", admin, Map.of("agentId", agentId));
        JsonNode unrelated = trip(admin, travelerId);

        for (String path : new String[]{"/api/trips", "/api/trips/" + tid, "/api/luggage/" + firstLuggage}) {
            String body = getJson(path, agent).toString();
            assertTrue(body.contains("Authorized recipient"));
            assertFalse(body.contains(hidden.get("id").asText()));
            assertFalse(body.contains(other.get("id").asText()));
            assertFalse(body.contains(direct.get("id").asText()));
            assertFalse(body.contains("Hidden recipient"));
            assertFalse(body.contains("Private administrator note"));
            assertFalse(body.contains(unrelated.get("id").asText()));
        }
        mvc.perform(get("/api/trips/" + unrelated.get("id").asText()).header("Authorization", "Bearer " + agent))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/luggage/" + secondLuggage).header("Authorization", "Bearer " + agent))
                .andExpect(status().isForbidden());
        JsonNode scanned = postJson("/api/scan", agent,
                Map.of("code", trip.get("luggage").get(0).get("luggageNumber").asText()));
        assertEquals(1, scanned.get("luggage").get("shipments").size());
        assertEquals(4, scanned.get("luggage").get("assignedWeightKg").asInt());
        assertEquals(8, getJson("/api/trips/" + tid, agent).get("assignedWeightKg").asInt());
        assertEquals(12, getJson("/api/trips/" + tid, agent).get("availableCapacityKg").asInt());
        assertEquals(8, getJson("/api/trips/" + tid, admin).get("assignedWeightKg").asInt());
        assertEquals(2, getJson("/api/luggage/" + firstLuggage, admin).get("shipments").size());
        long before = audits.count();
        for (String code : new String[]{hidden.get("trackingNumber").asText(), other.get("trackingNumber").asText(), secondCode}) {
            mvc.perform(post("/api/scan").header("Authorization", "Bearer " + agent)
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("code", code))))
                    .andExpect(status().isForbidden());
        }
        assertEquals(before, audits.count());
        postJson("/api/scan", agent, Map.of("code", allowed.get("trackingNumber").asText()));
        mvc.perform(get("/api/qr/luggage/" + firstLuggage).header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk());
        mvc.perform(get("/api/qr/shipment/" + hidden.get("id").asText()).header("Authorization", "Bearer " + agent))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/shipments/" + allowed.get("id").asText() + "/photos")
                .header("Authorization", "Bearer " + agent)).andExpect(status().isOk());
        mvc.perform(get("/api/shipments/" + hidden.get("id").asText() + "/photos")
                .header("Authorization", "Bearer " + agent)).andExpect(status().isForbidden());
        mvc.perform(put("/api/luggage/" + firstLuggage + "/status").header("Authorization", "Bearer " + agent)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PACKING\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/luggage/" + firstLuggage + "/shipments/" + allowed.get("id").asText())
                .header("Authorization", "Bearer " + agent)).andExpect(status().isForbidden());

        // Assigning the receiving agent authorizes all contents of that luggage, but not other luggage or direct packages.
        putJson("/api/luggage/" + firstLuggage + "/agent", admin, Map.of("agentId", agentId));
        assertEquals(2, getJson("/api/luggage/" + firstLuggage, agent).get("shipments").size());
        assertTrue(getJson("/api/shipments", agent).toString().contains(hidden.get("id").asText()));
        putJson("/api/luggage/" + firstLuggage + "/status", agent, Map.of("status", "PACKING"));
        putJson("/api/shipments/" + hidden.get("id").asText() + "/status", agent, Map.of("status", "RECEIVED_BY_AGENT"));
        mvc.perform(put("/api/luggage/" + secondLuggage + "/status").header("Authorization", "Bearer " + agent)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PACKING\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void travelersCannotEnumerateOtherTripsOrScanUnassignedShipments() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String traveler = login("traveler@example.com", "Traveler@123");
        JsonNode second = postJson("/api/admin/users", admin, Map.of("firstName", "Second", "lastName", "Traveler",
                "email", "manifest-traveler@example.com", "password", "Unique-traveler-password-2026", "role", "TRAVELER"));
        String secondToken = login("manifest-traveler@example.com", "Unique-traveler-password-2026");
        JsonNode otherTrip = trip(admin, second.get("id").asText());
        JsonNode item = shipment(admin, "Other traveler recipient");
        JsonNode sibling = shipment(admin, "Restricted sibling recipient");
        String tid = otherTrip.get("id").asText();
        String lid = otherTrip.get("luggage").get(0).get("id").asText();
        postJson("/api/luggage/" + lid + "/shipments", admin, Map.of("shipmentId", item.get("id").asText()));
        String siblingLuggage = otherTrip.get("luggage").get(1).get("id").asText();
        postJson("/api/luggage/" + siblingLuggage + "/shipments", admin, Map.of("shipmentId", sibling.get("id").asText()));
        for (String path : new String[]{"/api/trips/" + tid, "/api/luggage/" + lid, "/api/qr/luggage/" + lid}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + traveler)).andExpect(status().isForbidden());
        }
        assertFalse(getJson("/api/trips", traveler).toString().contains(tid));
        for (String code : new String[]{item.get("trackingNumber").asText(),
                otherTrip.get("luggage").get(0).get("luggageNumber").asText()}) {
            mvc.perform(post("/api/scan").header("Authorization", "Bearer " + traveler)
                    .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("code", code))))
                    .andExpect(status().isForbidden());
            postJson("/api/scan", secondToken, Map.of("code", code));
        }
        putJson("/api/luggage/" + lid + "/traveler", admin,
                Map.of("travelerId", getJson("/api/me", traveler).get("id").asText()));
        assertTrue(getJson("/api/trips", traveler).toString().contains(tid));
        JsonNode scoped = getJson("/api/trips/" + tid, traveler);
        assertEquals(1, scoped.get("luggage").size());
        assertFalse(scoped.toString().contains(sibling.get("id").asText()));
        assertFalse(scoped.toString().contains("Restricted sibling recipient"));
        postJson("/api/scan", traveler, Map.of("code", item.get("trackingNumber").asText()));
        mvc.perform(post("/api/scan").header("Authorization", "Bearer " + traveler)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("code", sibling.get("trackingNumber").asText()))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/trips/" + tid + "/status").header("Authorization", "Bearer " + traveler)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DEPARTED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentsCannotUsePackingMutationsToReadOrClaimOtherPackages() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String agent = login("agent@example.com", "Agent@123");
        JsonNode trip = trip(admin, getJson("/api/admin/users?role=TRAVELER", admin).get(0).get("id").asText());
        String lid = trip.get("luggage").get(0).get("id").asText();
        JsonNode item = shipment(admin, "Unassigned recipient");
        putJson("/api/luggage/" + lid + "/agent", admin,
                Map.of("agentId", getJson("/api/me", agent).get("id").asText()));
        mvc.perform(post("/api/luggage/" + lid + "/shipments").header("Authorization", "Bearer " + agent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("shipmentId", item.get("id").asText()))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/luggage/" + lid + "/shipments/batch").header("Authorization", "Bearer " + agent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("shipmentIds", java.util.List.of(item.get("id").asText())))))
                .andExpect(status().isForbidden());
        assertTrue(getJson("/api/shipments/" + item.get("id").asText(), admin).get("luggageId").isNull());
        assertEquals(0, getJson("/api/luggage/" + lid, admin).get("shipments").size());
    }
}
