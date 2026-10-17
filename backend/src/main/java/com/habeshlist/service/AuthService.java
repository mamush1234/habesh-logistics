package com.habeshlist.service;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.repository.*;
import com.habeshlist.security.JwtService;
import com.habeshlist.util.CodeGenerator;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Service
public class AuthService {

    private final UserRepository users;
    private final SenderProfileRepository senderProfiles;
    private final TravelerProfileRepository travelerProfiles;
    private final AgentProfileRepository agentProfiles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.habeshlist.security.LoginAttempts loginAttempts;

    public AuthService(UserRepository users, SenderProfileRepository senderProfiles,
                       TravelerProfileRepository travelerProfiles, AgentProfileRepository agentProfiles,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       com.habeshlist.security.LoginAttempts loginAttempts) {
        this.users = users;
        this.senderProfiles = senderProfiles;
        this.travelerProfiles = travelerProfiles;
        this.agentProfiles = agentProfiles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttempts = loginAttempts;
    }

    public ApiDtos.AuthResponse login(RequestDtos.LoginRequest req) {
        User u = users.findByEmailIgnoreCase(req.email().trim()).orElse(null);
        if (u == null) throw new BadCredentialsException("Invalid credentials");
        loginAttempts.check(u.getEmail());
        if (!u.isActive() || !passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        loginAttempts.succeeded(req.email());
        return response(u);
    }

    public ApiDtos.AuthResponse response(User user) {
        return new ApiDtos.AuthResponse(jwtService.generateToken(user), jwtService.getExpirationMs(), ApiDtos.UserDto.from(user));
    }

    @Transactional
    public ApiDtos.AuthResponse register(RequestDtos.RegisterRequest req) {
        if (req.role() != Role.SENDER && req.role() != Role.TRAVELER) {
            throw new BadRequestException("Only sender and traveler accounts can sign up. Contact an administrator for agent access.");
        }
        validateNewPassword(req.password());
        return response(createPrivilegedUser(req.firstName().trim(), req.lastName().trim(),
                req.email().trim(), req.phone(), req.password(), req.role()));
    }

    public static void validateNewPassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }

    }

    @Transactional
    public void changePassword(java.util.UUID id, RequestDtos.ChangePasswordRequest req) {
        User user = users.findForUpdate(id)
                .orElseThrow(() -> new com.habeshlist.exception.NotFoundException("User not found"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        validateNewPassword(req.newPassword());
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setCredentialVersion(user.getCredentialVersion() + 1);
    }

    @Transactional
    public User createPrivilegedUser(String firstName, String lastName, String email, String phone,
                                     String rawPassword, Role role) {
        email = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new BadRequestException("Email already registered");
        User u = User.builder()
                .firstName(firstName).lastName(lastName)
                .email(email).phone(phone)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role).active(true).build();
        users.save(u);
        switch (role) {
            case SENDER -> senderProfiles.save(SenderProfile.builder().user(u).build());
            case TRAVELER -> travelerProfiles.save(TravelerProfile.builder().user(u).build());
            case AGENT -> {
                String code = CodeGenerator.referralCode(firstName, Year.now().getValue());
                agentProfiles.save(AgentProfile.builder().user(u).active(true).referralCode(code).build());
            }
            default -> {}
        }
        return u;
    }
}
