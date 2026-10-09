package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.entity.User;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public ResponseEntity<ApiDtos.UserDto> me() {
        var p = SecurityUtils.currentUser();
        User u = users.findById(p.userId()).orElseThrow();
        return ResponseEntity.ok(ApiDtos.UserDto.from(u));
    }
}
