package com.habeshlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.repository.SenderProfileRepository;
import com.habeshlist.repository.TravelerProfileRepository;
import com.habeshlist.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository users;
    @Autowired SenderProfileRepository senders;
    @Autowired TravelerProfileRepository travelers;

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
        return om.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "", Map.of("email", email, "password", password)).get("token").asText();
    }

    private Map<String, String> registration(String email, String role) {
        return Map.of("firstName", "Public", "lastName", "Member", "email", email,
                "password", "Unique-member-password-2026", "role", role);
    }

    @Test
    void anonymousVisitorsSeeLandingDataButNotPrivateApplicationData() throws Exception {
        for (String path : new String[]{"/api/public/info", "/api/public/travel"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        for (String path : new String[]{"/api/shipments", "/api/trips", "/api/luggage", "/api/me",
                "/api/travel-announcements", "/api/travel-announcements/requests", "/api/package-queue",
                "/api/shipments/tracking/HL-000101", "/api/qr/tracking/HL-000101",
                "/api-docs", "/swagger-ui.html", "/actuator/health"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void senderAndTravelerSignUpWithoutInvitationsAndGetCorrectProfiles() throws Exception {
        for (String role : new String[]{"SENDER", "TRAVELER"}) {
            String email = "member-" + UUID.randomUUID() + "@example.com";
            JsonNode account = postJson("/api/auth/register", "", registration(email.toUpperCase(), role));
            assertEquals(role, account.get("user").get("role").asText());
            assertEquals(email, account.get("user").get("email").asText());
            UUID id = UUID.fromString(account.get("user").get("id").asText());
            assertEquals(role.equals("SENDER"), senders.existsById(id));
            assertEquals(role.equals("TRAVELER"), travelers.existsById(id));
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + account.get("token").asText()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role));
            login(email, "Unique-member-password-2026");
            mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + account.get("token").asText()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void publicRegistrationRejectsPrivilegedRolesDuplicatesAndInvalidPasswords() throws Exception {
        for (String role : new String[]{"ADMIN", "AGENT", "UNKNOWN"}) {
            String email = role.toLowerCase() + "-signup@example.com";
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(registration(email, role))))
                    .andExpect(status().isBadRequest());
            assertFalse(users.existsByEmailIgnoreCase(email));
        }
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(registration("SENDER@EXAMPLE.COM", "SENDER"))))
                .andExpect(status().isBadRequest());
        for (String password : new String[]{"short", "é".repeat(40)}) {
            var body = new java.util.HashMap<>(registration("invalid-pass@example.com", "SENDER"));
            body.put("password", password);
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        var missingRole = new java.util.HashMap<>(registration("no-role@example.com", "SENDER"));
        missingRole.remove("role");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(missingRole))).andExpect(status().isBadRequest());
    }

    @Test
    void invitationsAreRemovedAndOnlyAdminCanCreateAgents() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        mvc.perform(post("/api/auth/invitation").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"unused\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/invitations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        var body = registration("admin-created-agent@example.com", "AGENT");
        JsonNode agent = postJson("/api/admin/users", admin, body);
        assertEquals("AGENT", agent.get("role").asText());
        String sender = login("sender@example.com", "Sender@123");
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + sender)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicTripsContainOnlySafeUpcomingAvailableSummaries() throws Exception {
        String traveler = login("traveler@example.com", "Traveler@123");
        JsonNode trip = postJson("/api/marketplace/trips", traveler, Map.of("origin", "Washington, DC",
                "destination", "Addis Ababa", "departureDate", LocalDate.now().plusDays(10).toString(),
                "capacityKg", 12, "allowedItems", java.util.List.of("DOCUMENTS"), "blockedItems", java.util.List.of("FOOD")));
        mvc.perform(get("/api/public/travel")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(trip.get("id").asText()))
                .andExpect(jsonPath("$[0].availableCapacityKg").value(12))
                .andExpect(jsonPath("$[0].notes").doesNotExist())
                .andExpect(jsonPath("$[0].postedById").doesNotExist())
                .andExpect(jsonPath("$[0].postedByName").doesNotExist());
    }

    @Test
    void onlyAdminsManageAccessAndDisabledUsersLoseExistingSessions() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String sender = login("sender@example.com", "Sender@123");
        String id = users.findByEmailIgnoreCase("sender@example.com").orElseThrow().getId().toString();
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + sender)).andExpect(status().isForbidden());
        for (boolean active : new boolean[]{false, true}) {
            mvc.perform(put("/api/admin/users/" + id + "/access").header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"active\":" + active + "}")).andExpect(status().isOk());
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + sender)).andExpect(status().isUnauthorized());
        }
        String fresh = login("sender@example.com", "Sender@123");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
    }

    @Test
    void tokenRoleClaimsCannotOverrideDatabaseRoleAndDefaultSecretsAreRejected() throws Exception {
        var sender = users.findByEmailIgnoreCase("sender@example.com").orElseThrow();
        String forgedRole = Jwts.builder().subject(sender.getId().toString()).claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("test-secret-key-must-be-at-least-32-bytes-long-xx".getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + forgedRole)).andExpect(status().isForbidden());
        for (String secret : new String[]{"", "short", "change-me-in-prod-this-must-be-at-least-32-bytes-long-base64-ok",
                "this-is-a-dev-secret-change-me-at-least-32-bytes-long"}) {
            assertThrows(IllegalStateException.class, () -> new JwtService(secret, 3600000));
        }
        mvc.perform(get("/api/me").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeInvalidatesOldTokensAndRequiresCurrentPassword() throws Exception {
        String session = login("sender2@example.com", "Sender@123");
        mvc.perform(put("/api/me/password").header("Authorization", "Bearer " + session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"A-unique-password-2026\"}")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/password").header("Authorization", "Bearer " + session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Sender@123\",\"newPassword\":\"A-unique-password-2026\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + session)).andExpect(status().isUnauthorized());
        login("sender2@example.com", "A-unique-password-2026");
    }

    @Test
    void loginAttemptsAreLimitedAndQrRequiresOwnership() throws Exception {
        String email = "limited-" + UUID.randomUUID() + "@example.com";
        postJson("/api/auth/register", "", registration(email, "SENDER"));
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of("email", email, "password", "wrong")))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", email.toUpperCase(), "password", "Unique-member-password-2026"))))
                .andExpect(status().isTooManyRequests());
        String sender = login("sender@example.com", "Sender@123");
        mvc.perform(get("/api/qr/tracking/HL-000101").header("Authorization", "Bearer " + sender))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/qr/tracking/HL-000104").header("Authorization", "Bearer " + sender)).andExpect(status().isForbidden());
    }
}
