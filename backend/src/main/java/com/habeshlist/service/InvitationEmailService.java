package com.habeshlist.service;

import com.habeshlist.dto.ApiDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class InvitationEmailService {
    private static final Logger LOG = LoggerFactory.getLogger(InvitationEmailService.class);
    private final JavaMailSender mail;
    private final boolean enabled;
    private final String from;
    private final String websiteUrl;

    public InvitationEmailService(JavaMailSender mail,
            @Value("${app.invitations.email-enabled}") boolean enabled,
            @Value("${app.invitations.from}") String from,
            @Value("${app.invitations.website-url}") String websiteUrl,
            @Value("${spring.mail.username}") String username,
            @Value("${spring.mail.password}") String password) {
        this.mail = mail;
        this.enabled = enabled;
        this.from = from;
        URI website = URI.create(websiteUrl);
        boolean local = "localhost".equals(website.getHost()) || "127.0.0.1".equals(website.getHost());
        if (website.getHost() == null || website.getUserInfo() != null || website.getQuery() != null
                || website.getFragment() != null || (website.getPath() != null && !website.getPath().isEmpty()
                && !website.getPath().equals("/"))
                || !("https".equals(website.getScheme()) || (local && "http".equals(website.getScheme())))) {
            throw new IllegalStateException("APP_WEBSITE_URL must be the HTTPS website origin (HTTP allowed only for localhost)");
        }
        this.websiteUrl = websiteUrl.replaceAll("/$", "");
        if (enabled && (username.isBlank() || password.isBlank() || from.isBlank()
                || from.contains("\r") || from.contains("\n") || !from.contains("@"))) {
            throw new IllegalStateException("Invitation email requires SMTP_USERNAME, SMTP_PASSWORD and a valid SMTP_FROM");
        }
    }

    public ApiDtos.InvitationDeliveryDto deliver(ApiDtos.CreatedInvitationDto created) {
        String link = websiteUrl + "/register#invite=" + created.token();
        if (!enabled) {
            LOG.warn("Invitation {} created without email: SMTP delivery is disabled", created.invitation().id());
            return new ApiDtos.InvitationDeliveryDto(created.invitation(), created.token(), link, false,
                    "Email was not sent: invitation email is disabled. Configure Gmail SMTP or share the link privately.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(created.invitation().email());
        message.setSubject("Your private Habesh Logistics invitation");
        message.setText("You have been invited to Habesh Logistics as "
                + created.invitation().role().name() + ".\n\n"
                + "Create your account using this personal link:\n" + link + "\n\n"
                + "This link expires at " + created.invitation().expiresAt()
                + " (UTC) and can be used only once. Do not forward it.\n\n"
                + "If you were not expecting this invitation, ignore this email.");
        try {
            mail.send(message);
            return new ApiDtos.InvitationDeliveryDto(created.invitation(), created.token(), link, true,
                    "Invitation accepted by the email server. Check the recipient's inbox and spam folder.");
        } catch (MailException ex) {
            // SMTP diagnostics can contain credentials or the invitation; do not log the exception body.
            LOG.warn("Email delivery failed for invitation {} ({})",
                    created.invitation().id(), ex.getClass().getSimpleName());
            return new ApiDtos.InvitationDeliveryDto(created.invitation(), created.token(), link, false,
                    "Invitation created, but email could not be sent. Check Gmail SMTP settings or share the link privately.");
        }
    }
}
