package com.habeshlist.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshlist.agent.AgentCommissionService;
import com.habeshlist.entity.DeliveryRequest;
import com.habeshlist.entity.MarketTrip;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.MarketplaceTypes.EscrowStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.VerificationLevel;
import com.habeshlist.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminConsoleServiceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AgentCommissionService commissionService;

    @Test
    void userStatusReflectsSuspensionAndVerification() {
        User traveler = User.builder().role(Role.TRAVELER).active(true).build();
        assertEquals("PENDING", AdminConsoleService.userStatus(traveler, VerificationLevel.UNVERIFIED, null));
        assertEquals("VERIFIED", AdminConsoleService.userStatus(traveler, VerificationLevel.ID_VERIFIED, null));
        assertEquals("PENDING", AdminConsoleService.userStatus(User.builder().role(Role.AGENT).active(true).build(), null, null));
        assertEquals("VERIFIED", AdminConsoleService.userStatus(User.builder().role(Role.AGENT).active(true).build(), null, true));
        assertEquals("PENDING", AdminConsoleService.userStatus(User.builder().role(Role.SENDER).active(true).mustChangePassword(true).build(), null, null));
        traveler.setActive(false);
        assertEquals("SUSPENDED", AdminConsoleService.userStatus(traveler, VerificationLevel.FULLY_VERIFIED, null));
    }

    @Test
    void platformFeeAndPayoutFollowTheFeePayer() {
        DeliveryRequest r = DeliveryRequest.builder().id(UUID.randomUUID()).pickupCity("DC").destinationCity("Addis")
                .escrowAmount(new BigDecimal("100.00")).escrowStatus(EscrowStatus.DEMO_HELD).build();
        PlatformSettings s = AdminConsoleService.defaults();
        s.setPlatformFeePercent(new BigDecimal("12.50"));
        assertEquals(new BigDecimal("12.50"), AdminConsoleService.payment(r, Instant.now(), s).platformFee());
        assertEquals(new BigDecimal("100.00"), AdminConsoleService.payment(r, Instant.now(), s).travelerPayout());
        s.setFeePayer(PlatformSettings.FeePayer.TRAVELER);
        assertEquals(new BigDecimal("87.50"), AdminConsoleService.payment(r, Instant.now(), s).travelerPayout());
        s.setFeePayer(PlatformSettings.FeePayer.SPLIT);
        assertEquals(new BigDecimal("93.75"), AdminConsoleService.payment(r, Instant.now(), s).travelerPayout());
    }

    @Test
    void paginationClampsPagesAndTripStatusUsesDates() {
        List<Integer> rows = IntStream.range(0, 23).boxed().toList();
        var last = AdminConsoleService.paginate(rows, 99, Map.of());
        assertEquals(2, last.page());
        assertEquals(3, last.totalPages());
        assertEquals(List.of(20, 21, 22), last.items());
        assertEquals(1, AdminConsoleService.paginate(List.of(), 0, Map.of()).totalPages());

        LocalDate today = LocalDate.of(2026, 5, 10);
        assertEquals("UPCOMING", AdminConsoleService.tripStatus(trip(today.plusDays(1), null), today));
        assertEquals("IN_PROGRESS", AdminConsoleService.tripStatus(trip(today.minusDays(1), today.plusDays(1)), today));
        assertEquals("COMPLETED", AdminConsoleService.tripStatus(trip(today.minusDays(3), today.minusDays(1)), today));
    }

    @Test
    void consoleEndpointsAreAdminOnly() throws Exception {
        call("GET", "/api/admin/console/overview", null, null).andExpect(status().isUnauthorized());
        call("GET", "/api/admin/console/overview", login("sender@example.com", "Sender@123"), null).andExpect(status().isForbidden());
        call("GET", "/api/admin/console/users", login("agent@example.com", "Agent@123"), null).andExpect(status().isForbidden());
        call("GET", "/api/admin/console/settings", login("traveler@example.com", "Traveler@123"), null).andExpect(status().isForbidden());
        String admin = admin();
        for (String path : List.of("overview", "users", "trips", "requests", "requests?view=tracking",
                "requests?view=disputes", "payments", "commissions", "reports", "settings"))
            call("GET", "/api/admin/console/" + path, admin, null).andExpect(status().isOk());
        call("GET", "/api/admin/console/requests?view=nope", admin, null).andExpect(status().isBadRequest());
        call("GET", "/api/admin/console/overview?from=2026-02-01&to=2026-01-01", admin, null).andExpect(status().isBadRequest());
    }

    @Test
    void overviewBucketsByDayOrWeek() throws Exception {
        String admin = admin();
        JsonNode daily = ok("GET", "/api/admin/console/overview?from=2026-01-01&to=2026-01-07", admin, null);
        assertEquals("DAY", daily.get("bucket").asText());
        assertEquals(7, daily.get("chart").size());
        JsonNode weekly = ok("GET", "/api/admin/console/overview?from=2026-01-01&to=2026-03-31", admin, null);
        assertEquals("WEEK", weekly.get("bucket").asText());
        assertEquals("2025-12-29", weekly.get("chart").get(0).get("date").asText());
    }

    @Test
    void userSearchFiltersByTypeAndQuery() throws Exception {
        JsonNode travelers = ok("GET", "/api/admin/console/users?type=travelers", admin(), null);
        assertTrue(travelers.get("items").size() > 0);
        travelers.get("items").forEach(u -> assertEquals("TRAVELER", u.get("role").asText()));
        assertTrue(travelers.get("counts").get("ALL").asLong() >= travelers.get("totalItems").asLong());
        JsonNode hit = ok("GET", "/api/admin/console/users?q=traveler@example.com", admin(), null);
        assertEquals(1, hit.get("totalItems").asInt());
    }

    @Test
    void settingsAreValidatedPersistedAndDriveTheDefaultAgentRate() throws Exception {
        String admin = admin();
        call("PUT", "/api/admin/console/settings", admin,
                Map.of("platformFeePercent", 101, "agentCommissionPercent", 5, "feePayer", "SENDER"))
                .andExpect(status().isBadRequest());
        call("PUT", "/api/admin/console/settings", admin,
                Map.of("platformFeePercent", 8, "agentCommissionPercent", 5, "feePayer", "NOBODY"))
                .andExpect(status().isBadRequest());
        JsonNode saved = ok("PUT", "/api/admin/console/settings", admin,
                Map.of("platformFeePercent", 8.5, "agentCommissionPercent", 7.25, "feePayer", "SPLIT"));
        assertEquals("SPLIT", saved.get("feePayer").asText());
        assertFalse(saved.get("updatedBy").isNull());
        JsonNode read = ok("GET", "/api/admin/console/settings", admin, null);
        assertEquals(0, new BigDecimal("8.50").compareTo(read.get("platformFeePercent").decimalValue()));
        assertEquals(0, new BigDecimal("0.0725").compareTo(commissionService.rateFor(UUID.randomUUID())));
    }

    private static MarketTrip trip(LocalDate departure, LocalDate arrival) {
        return MarketTrip.builder().departureDate(departure).expectedArrivalDate(arrival).build();
    }

    private String admin() throws Exception { return login("admin@habeshlist.com", "Admin@123"); }

    private String login(String email, String password) throws Exception {
        return ok("POST", "/api/auth/login", null, Map.of("email", email, "password", password)).get("token").asText();
    }

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
}
