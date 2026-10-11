package com.habeshlist.service;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.Invitation;
import com.habeshlist.entity.User;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.InvitationRepository;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.util.CodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class InvitationService {
    private final InvitationRepository invitations;
    private final UserRepository users;
    private final AuthService auth;

    public InvitationService(InvitationRepository invitations, UserRepository users, AuthService auth) {
        this.invitations = invitations;
        this.users = users;
        this.auth = auth;
    }

    @Transactional
    public ApiDtos.CreatedInvitationDto create(RequestDtos.CreateInvitationRequest req) {
        Role role;
        try { role = Role.valueOf(req.role()); }
        catch (IllegalArgumentException ex) { throw new BadRequestException("Invalid invitation role"); }
        if (role == Role.ADMIN) throw new BadRequestException("Administrator invitations are not allowed");
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new BadRequestException("Email already registered");
        String token = CodeGenerator.randomToken(48);
        Instant now = Instant.now();
        Invitation invitation = invitations.save(Invitation.builder().tokenHash(hash(token))
                .email(email).role(role).createdAt(now).expiresAt(now.plus(48, ChronoUnit.HOURS)).build());
        return new ApiDtos.CreatedInvitationDto(ApiDtos.InvitationDto.from(invitation), token);
    }

    @Transactional(readOnly = true)
    public ApiDtos.InvitationInfoDto inspect(String token) {
        Invitation invitation = valid(invitations.findByTokenHash(hash(token))
                .orElseThrow(InvitationService::invalid));
        return new ApiDtos.InvitationInfoDto(invitation.getEmail(), invitation.getRole(), invitation.getExpiresAt());
    }

    @Transactional
    public ApiDtos.AuthResponse redeem(RequestDtos.RegisterRequest req) {
        Invitation invitation = valid(invitations.findForRedemption(hash(req.invitationToken()))
                .orElseThrow(InvitationService::invalid));
        if (!invitation.getEmail().equalsIgnoreCase(req.email().trim())) throw invalid();
        AuthService.validateNewPassword(req.password());
        User user = auth.createPrivilegedUser(req.firstName(), req.lastName(), invitation.getEmail(),
                req.phone(), req.password(), invitation.getRole());
        invitation.setUsedAt(Instant.now());
        return auth.response(user);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.InvitationDto> list() {
        return invitations.findTop100ByOrderByCreatedAtDesc().stream().map(ApiDtos.InvitationDto::from).toList();
    }

    @Transactional
    public void revoke(UUID id) {
        Invitation invitation = invitations.findForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
        invitation.setRevokedAt(Instant.now());
    }

    private static Invitation valid(Invitation invitation) {
        if (invitation.getUsedAt() != null || invitation.getRevokedAt() != null
                || !invitation.getExpiresAt().isAfter(Instant.now())) throw invalid();
        return invitation;
    }

    private static BadRequestException invalid() {
        return new BadRequestException("Invitation is invalid, expired or already used. Contact your administrator.");
    }

    private static String hash(String token) {
        if (token == null || token.length() != 48) throw invalid();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
