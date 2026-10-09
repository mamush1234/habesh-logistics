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

    public AuthService(UserRepository users, SenderProfileRepository senderProfiles,
                       TravelerProfileRepository travelerProfiles, AgentProfileRepository agentProfiles,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.senderProfiles = senderProfiles;
        this.travelerProfiles = travelerProfiles;
        this.agentProfiles = agentProfiles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public ApiDtos.AuthResponse register(RequestDtos.RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new BadRequestException("Email already registered");
        }
        // Public registration allows SENDER or TRAVELER only; ADMIN/AGENT are created by admins
        Role role = Role.SENDER;
        if (req.role() != null && !req.role().isBlank()) {
            try { role = Role.valueOf(req.role().trim().toUpperCase()); }
            catch (IllegalArgumentException e) { throw new BadRequestException("Invalid category"); }
            if (role != Role.SENDER && role != Role.TRAVELER) {
                throw new BadRequestException("Category must be SENDER or TRAVELER");
            }
        }
        User u = User.builder()
                .firstName(req.firstName())
                .lastName(req.lastName())
                .email(req.email().toLowerCase())
                .phone(req.phone())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(role)
                .active(true)
                .build();
        users.save(u);
        if (role == Role.TRAVELER) {
            travelerProfiles.save(TravelerProfile.builder().user(u).build());
        } else {
            senderProfiles.save(SenderProfile.builder().user(u).build());
        }
        return new ApiDtos.AuthResponse(jwtService.generateToken(u), jwtService.getExpirationMs(), ApiDtos.UserDto.from(u));
    }

    public ApiDtos.AuthResponse login(RequestDtos.LoginRequest req) {
        User u = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!u.isActive() || !passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return new ApiDtos.AuthResponse(jwtService.generateToken(u), jwtService.getExpirationMs(), ApiDtos.UserDto.from(u));
    }

    @Transactional
    public User createPrivilegedUser(String firstName, String lastName, String email, String phone,
                                     String rawPassword, Role role) {
        if (users.existsByEmailIgnoreCase(email)) throw new BadRequestException("Email already registered");
        User u = User.builder()
                .firstName(firstName).lastName(lastName)
                .email(email.toLowerCase()).phone(phone)
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
