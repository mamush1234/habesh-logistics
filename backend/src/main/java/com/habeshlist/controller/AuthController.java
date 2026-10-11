package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.service.AuthService;
import com.habeshlist.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final InvitationService invitations;

    public AuthController(AuthService authService, InvitationService invitations) {
        this.authService = authService;
        this.invitations = invitations;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiDtos.AuthResponse> register(@Valid @RequestBody RequestDtos.RegisterRequest req) {
        return ResponseEntity.ok(invitations.redeem(req));
    }

    @PostMapping("/invitation")
    public ApiDtos.InvitationInfoDto invitation(@Valid @RequestBody RequestDtos.InspectInvitationRequest req) {
        return invitations.inspect(req.token());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiDtos.AuthResponse> login(@Valid @RequestBody RequestDtos.LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
