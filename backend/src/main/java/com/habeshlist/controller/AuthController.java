package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final com.habeshlist.service.SenderOnboardingService senderOnboarding;
    private final com.habeshlist.service.TravelerOnboardingService travelerOnboarding;

    public AuthController(AuthService authService, com.habeshlist.service.SenderOnboardingService senderOnboarding,
                          com.habeshlist.service.TravelerOnboardingService travelerOnboarding) {
        this.authService = authService;
        this.senderOnboarding = senderOnboarding;
        this.travelerOnboarding = travelerOnboarding;
    }

    @PostMapping("/register/traveler")
    public ResponseEntity<com.habeshlist.dto.TravelerOnboardingDtos.Result> traveler(
            @Valid @RequestBody com.habeshlist.dto.TravelerOnboardingDtos.Input input) {
        return ResponseEntity.ok(travelerOnboarding.register(input));
    }

    @PostMapping("/register/sender")
    public ResponseEntity<com.habeshlist.dto.SenderOnboardingDtos.Result> sender(
            @Valid @RequestBody com.habeshlist.dto.SenderOnboardingDtos.Input input) {
        return ResponseEntity.ok(senderOnboarding.register(input));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiDtos.AuthResponse> register(@Valid @RequestBody RequestDtos.RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiDtos.AuthResponse> login(@Valid @RequestBody RequestDtos.LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
