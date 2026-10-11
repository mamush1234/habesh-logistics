package com.habeshlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshlist.repository.InvitationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.invitations.email-enabled=true",
        "app.invitations.website-url=https://travel.habeshlist.com",
        "spring.mail.username=habeshalist@gmail.com",
        "spring.mail.password=test-only-app-password"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvitationEmailIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired InvitationRepository invitations;
    @MockBean JavaMailSender mail;

    private JsonNode postJson(String path, String token, Object payload) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(payload));
        if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
        return om.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void committedInvitationRemainsUsableAfterEmailFailureAndSuccessIsExplicit() throws Exception {
        String admin = postJson("/api/auth/login", "",
                Map.of("email", "admin@habeshlist.com", "password", "Admin@123")).get("token").asText();
        for (boolean fail : new boolean[]{true, false}) {
            reset(mail);
            if (fail) doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
            String email = "email-" + UUID.randomUUID() + "@example.com";
            JsonNode response = postJson("/api/admin/invitations", admin, Map.of("email", email, "role", "SENDER"));
            UUID id = UUID.fromString(response.get("invitation").get("id").asText());
            try {
                assertEquals(!fail, response.get("emailSent").asBoolean());
                assertTrue(invitations.existsById(id));
                assertTrue(response.get("invitationUrl").asText().startsWith("https://travel.habeshlist.com/register#invite="));
                assertEquals(email, postJson("/api/auth/invitation", "",
                        Map.of("token", response.get("token").asText())).get("email").asText());
                verify(mail).send(any(SimpleMailMessage.class));
            } finally {
                invitations.deleteById(id);
            }
        }
    }
}
