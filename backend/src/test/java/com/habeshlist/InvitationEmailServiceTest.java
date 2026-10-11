package com.habeshlist;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.service.InvitationEmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvitationEmailServiceTest {
    private final JavaMailSender mail = mock(JavaMailSender.class);

    private InvitationEmailService service(boolean enabled) {
        return new InvitationEmailService(mail, enabled, "habeshalist@gmail.com",
                "https://travel.habeshlist.com/", "habeshalist@gmail.com", "test-only-app-password");
    }

    private ApiDtos.CreatedInvitationDto invitation() {
        Instant now = Instant.now();
        return new ApiDtos.CreatedInvitationDto(new ApiDtos.InvitationDto(UUID.randomUUID(),
                "invited@example.com", Role.SENDER, now, now.plusSeconds(172800), null, null), "X".repeat(48));
    }

    @Test
    void sendsToInviteeUsingConfiguredSenderAndPublicWebsiteUrl() {
        var created = invitation();
        var result = service(true).deliver(created);
        assertTrue(result.emailSent());
        assertEquals("https://travel.habeshlist.com/register#invite=" + created.token(), result.invitationUrl());
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertEquals("habeshalist@gmail.com", message.getValue().getFrom());
        assertArrayEquals(new String[]{"invited@example.com"}, message.getValue().getTo());
        assertTrue(message.getValue().getText().contains(result.invitationUrl()));
        assertTrue(message.getValue().getText().contains(created.invitation().expiresAt().toString()));
        assertFalse(message.getValue().getText().contains("test-only-app-password"));
    }

    @Test
    void disabledEmailExplicitlyReportsNotSentAndRetainsManualLink() {
        var result = service(false).deliver(invitation());
        assertFalse(result.emailSent());
        assertTrue(result.deliveryMessage().contains("disabled"));
        assertFalse(result.invitationUrl().isBlank());
        verifyNoInteractions(mail);
    }

    @Test
    void authenticationAndConnectionFailuresAreNotReportedAsSuccess() {
        for (var error : new org.springframework.mail.MailException[]{
                new MailAuthenticationException("sensitive SMTP diagnostic"),
                new MailSendException("connection failed")}) {
            reset(mail);
            doThrow(error).when(mail).send(any(SimpleMailMessage.class));
            var result = service(true).deliver(invitation());
            assertFalse(result.emailSent());
            assertTrue(result.deliveryMessage().contains("could not be sent"));
            assertFalse(result.deliveryMessage().contains(error.getMessage()));
            assertFalse(result.invitationUrl().isBlank());
        }
    }

    @Test
    void enabledEmailRequiresCredentialsAndSafeWebsiteOrigin() {
        assertThrows(IllegalStateException.class, () -> new InvitationEmailService(mail, true,
                "habeshalist@gmail.com", "https://travel.habeshlist.com", "habeshalist@gmail.com", ""));
        for (String url : new String[]{"http://travel.habeshlist.com", "https://example.com/?token=x",
                "https://example.com/#fragment", "https://user:password@example.com", "https://example.com/path"}) {
            assertThrows(IllegalStateException.class, () -> new InvitationEmailService(mail, false,
                    "habeshalist@gmail.com", url, "", ""));
        }
        assertDoesNotThrow(() -> new InvitationEmailService(mail, false,
                "habeshalist@gmail.com", "http://localhost:3000", "", ""));
    }
}
