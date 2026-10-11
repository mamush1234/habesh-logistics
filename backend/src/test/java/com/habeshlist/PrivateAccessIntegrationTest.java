package com.habeshlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshlist.entity.Invitation;
import com.habeshlist.repository.InvitationRepository;
import com.habeshlist.repository.UserRepository;
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
import java.time.Instant;
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
class PrivateAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired InvitationRepository invitations;
    @Autowired UserRepository users;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
        return om.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "", Map.of("email", email, "password", password)).get("token").asText();
    }

    private JsonNode invite(String admin, String email, String role) throws Exception {
        return postJson("/api/admin/invitations", admin, Map.of("email", email, "role", role));
    }

    private Map<String, String> registration(String email, String token) {
        return Map.of("firstName", "Invited", "lastName", "User", "email", email,
                "password", "Unique-invite-password-2026", "invitationToken", token, "role", "ADMIN");
    }

    @Test
    void anonymousVisitorsCannotReadAnyApplicationDataOrCreateShipments() throws Exception {
        for (String path : new String[]{"/api/public/info", "/api/shipments", "/api/trips", "/api/luggage",
                "/api/admin/invitations", "/api/me", "/api/shipments/tracking/HL-000101",
                "/api/qr/tracking/HL-000101", "/api-docs", "/swagger-ui.html", "/actuator/health"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/shipments/guest").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("firstName", "Public", "lastName", "User",
                        "email", "public@example.com", "password", "Public-password-2026"))))
                .andExpect(status().isBadRequest());
        assertFalse(users.existsByEmailIgnoreCase("public@example.com"));
    }

    @Test
    void invitationIsEmailBoundSingleUseAndCannotGrantAdministratorRole() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String email = "invited-" + UUID.randomUUID() + "@example.com";
        JsonNode created = invite(admin, email, "SENDER");
        String token = created.get("token").asText();
        String id = created.get("invitation").get("id").asText();
        Invitation stored = invitations.findById(UUID.fromString(id)).orElseThrow();
        assertEquals(64, stored.getTokenHash().length());
        assertNotEquals(token, stored.getTokenHash());
        mvc.perform(get("/api/admin/invitations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].tokenHash").doesNotExist())
                .andExpect(jsonPath("$[0].token").doesNotExist());
        postJson("/api/auth/invitation", "", Map.of("token", token));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(registration("wrong@example.com", token))))
                .andExpect(status().isBadRequest());
        JsonNode account = postJson("/api/auth/register", "", registration(email, token));
        assertEquals("SENDER", account.get("user").get("role").asText());
        String session = account.get("token").asText();
        entityManager.flush();
        entityManager.clear();
        assertNotNull(invitations.findById(UUID.fromString(id)).orElseThrow().getUsedAt());
        mvc.perform(get("/api/public/info").header("Authorization", "Bearer " + session)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + session))
                .andExpect(status().isForbidden());
        for (String path : new String[]{"/api/auth/register", "/api/auth/invitation"}) {
            Object body = path.endsWith("/register") ? registration(email, token) : Map.of("token", token);
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/invitations").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "admin-invite@example.com", "role", "ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredRevokedAndInvalidInvitationsCannotBeRedeemed() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        for (String mode : new String[]{"expired", "revoked"}) {
            String email = mode + "-" + UUID.randomUUID() + "@example.com";
            JsonNode created = invite(admin, email, "TRAVELER");
            String token = created.get("token").asText();
            UUID id = UUID.fromString(created.get("invitation").get("id").asText());
            if (mode.equals("expired")) invitations.findById(id).orElseThrow().setExpiresAt(Instant.now().minusSeconds(1));
            else mvc.perform(delete("/api/admin/invitations/" + id).header("Authorization", "Bearer " + admin))
                    .andExpect(status().isNoContent());
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(registration(email, token)))).andExpect(status().isBadRequest());
            assertFalse(users.existsByEmailIgnoreCase(email));
        }
        mvc.perform(post("/api/auth/invitation").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("token", "X".repeat(48)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsManageInvitationsAndDisabledUsersLoseExistingSessions() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String sender = login("sender@example.com", "Sender@123");
        String userId = users.findByEmailIgnoreCase("sender@example.com").orElseThrow().getId().toString();
        for (String path : new String[]{"/api/admin/invitations", "/api/admin/users"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + sender)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/invitations").header("Authorization", "Bearer " + sender)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "invite@example.com", "role", "SENDER"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/" + userId + "/access").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + sender)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"sender@example.com\",\"password\":\"Sender@123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/users/" + userId + "/access").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + sender)).andExpect(status().isUnauthorized());
        String fresh = login("sender@example.com", "Sender@123");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
    }

    @Test
    void tokenRoleClaimsCannotOverrideDatabaseRoleAndDefaultSecretsAreRejected() throws Exception {
        var sender = users.findByEmailIgnoreCase("sender@example.com").orElseThrow();
        String forgedRole = Jwts.builder().subject(sender.getId().toString()).claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("test-secret-key-must-be-at-least-32-bytes-long-xx".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + forgedRole))
                .andExpect(status().isForbidden());
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
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"A-unique-password-2026\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/password").header("Authorization", "Bearer " + session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Sender@123\",\"newPassword\":\"A-unique-password-2026\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + session)).andExpect(status().isUnauthorized());
        String fresh = login("sender2@example.com", "A-unique-password-2026");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
    }

    @Test
    void loginAttemptsAreLimitedAndQrRequiresOwnership() throws Exception {
        String admin = login("admin@habeshlist.com", "Admin@123");
        String email = "limited-" + UUID.randomUUID() + "@example.com";
        JsonNode created = invite(admin, email, "SENDER");
        postJson("/api/auth/register", "", registration(email, created.get("token").asText()));
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of("email", email, "password", "wrong"))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", email.toUpperCase(), "password", "Unique-invite-password-2026"))))
                .andExpect(status().isTooManyRequests());
        String sender = login("sender@example.com", "Sender@123");
        mvc.perform(get("/api/qr/tracking/HL-000101").header("Authorization", "Bearer " + sender))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/qr/tracking/HL-000104").header("Authorization", "Bearer " + sender))
                .andExpect(status().isForbidden());
    }
}
