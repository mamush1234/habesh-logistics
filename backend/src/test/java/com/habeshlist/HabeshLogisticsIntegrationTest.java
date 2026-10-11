package com.habeshlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HabeshLogisticsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String login(String email, String pw) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + pw + "\"}";
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode j = om.readTree(r.getResponse().getContentAsString());
        return j.get("token").asText();
    }

    @Test
    void adminLoginWorks() throws Exception {
        String t = login("admin@habeshlist.com", "Admin@123");
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShipments", is(5)));
    }

    @Test
    void senderCannotSeeOtherSendersShipments() throws Exception {
        String t = login("sender@example.com", "Sender@123");
        MvcResult r = mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn();
        JsonNode arr = om.readTree(r.getResponse().getContentAsString());
        // Sender 1 owns HL-000101..HL-000103
        for (JsonNode node : arr) {
            String tn = node.get("trackingNumber").asText();
            assert tn.equals("HL-000101") || tn.equals("HL-000102") || tn.equals("HL-000103");
        }
    }

    @Test
    void trackingRequiresLoginAndOwnership() throws Exception {
        mvc.perform(get("/api/shipments/tracking/HL-000101")).andExpect(status().isUnauthorized());
        String t = login("sender@example.com", "Sender@123");
        MvcResult r = mvc.perform(get("/api/shipments/tracking/HL-000101").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber", is("HL-000101")))
                .andReturn();
        String body = r.getResponse().getContentAsString();
        // Must NOT expose senderName, senderId, recipient details, luggage number
        assert !body.contains("senderName");
        assert !body.contains("recipient");
        assert !body.contains("LUG-");
    }

    @Test
    void luggageCapacityIsEnforced() throws Exception {
        String t = login("admin@habeshlist.com", "Admin@123");
        // HL-000102 is already assigned to LUG-01 (seed). Try to re-assign to a brand new 1kg luggage.
        String tripBody = "{" +
                "\"travelerId\":\"" + getTravelerId(t) + "\"," +
                "\"departureAirport\":\"IAD\",\"arrivalAirport\":\"ADD\"," +
                "\"departureDate\":\"2026-12-01\",\"totalLuggageCapacity\":1," +
                "\"numberOfLuggage\":1}";
        MvcResult tr = mvc.perform(post("/api/trips").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content(tripBody))
                .andExpect(status().isOk()).andReturn();
        JsonNode trip = om.readTree(tr.getResponse().getContentAsString());
        String luggageId = trip.get("luggage").get(0).get("id").asText();

        String shipmentId = getShipmentIdByTn(t, "HL-000102"); // 8 kg, already assigned
        String assignBody = "{\"shipmentId\":\"" + shipmentId + "\"}";
        // Should fail: either because already-assigned, or capacity exceeded
        mvc.perform(post("/api/luggage/" + luggageId + "/shipments")
                .header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content(assignBody))
                .andExpect(status().isBadRequest());
    }

    private String getTravelerId(String token) throws Exception {
        MvcResult r = mvc.perform(get("/api/admin/users?role=TRAVELER").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    private String getShipmentIdByTn(String token, String tn) throws Exception {
        MvcResult r = mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode n : om.readTree(r.getResponse().getContentAsString())) {
            if (n.get("trackingNumber").asText().equals(tn)) return n.get("id").asText();
        }
        throw new RuntimeException("not found");
    }

    @Test
    void senderCannotReadAnotherSendersShipmentById() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String otherShipmentId = getShipmentIdByTn(admin, "HL-000104"); // belongs to sender2

        String sender1Token = login("sender@example.com", "Sender@123");
        mvc.perform(get("/api/shipments/" + otherShipmentId).header("Authorization", "Bearer " + sender1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void referralCommissionCalculatedForSeededShipment() throws Exception {
        String agentToken = login("agent@example.com", "Agent@123");
        MvcResult r = mvc.perform(get("/api/agents/me/referrals").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode j = om.readTree(r.getResponse().getContentAsString());
        // HL-000101 was referred by agent, 5 kg x $10/kg = $50 revenue, 10% commission = $5
        assert j.get("totalReferrals").asInt() == 1;
        assert new BigDecimal(j.get("commissionEarned").asText()).compareTo(new BigDecimal("5.00")) == 0;
    }
}
