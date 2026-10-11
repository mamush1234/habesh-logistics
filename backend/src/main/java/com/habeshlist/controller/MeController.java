package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.entity.User;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.service.AuthService;
import com.habeshlist.dto.request.RequestDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository users;
    private final AuthService auth;

    public MeController(UserRepository users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @org.springframework.web.bind.annotation.PutMapping("/password")
    public ResponseEntity<Void> password(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody RequestDtos.ChangePasswordRequest req) {
        auth.changePassword(SecurityUtils.currentUser().userId(), req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<ApiDtos.UserDto> me() {
        var p = SecurityUtils.currentUser();
        User u = users.findById(p.userId()).orElseThrow();
        return ResponseEntity.ok(ApiDtos.UserDto.from(u));
    }
}
