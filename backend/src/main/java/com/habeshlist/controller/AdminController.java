package com.habeshlist.controller;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.repository.UserRepository;
import com.habeshlist.service.AuthService;
import com.habeshlist.service.ReferralService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ReferralService referralService;
    private final UserRepository users;
    private final AuthService authService;
    private final com.habeshlist.repository.AgentProfileRepository agentProfiles;

    @Value("${app.alexandria.address}")
    private String alexandriaAddress;
    @Value("${app.alexandria.hours}")
    private String alexandriaHours;

    public AdminController(ReferralService referralService, UserRepository users, AuthService authService,
                           com.habeshlist.repository.AgentProfileRepository agentProfiles) {
        this.agentProfiles = agentProfiles;
        this.referralService = referralService;
        this.users = users;
        this.authService = authService;
    }

    @PostMapping("/users")
    public ApiDtos.UserDto createUser(@Valid @RequestBody RequestDtos.AdminCreateUserRequest req) {
        Role role;
        try { role = Role.valueOf(req.role().trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new BadRequestException("Invalid role"); }
        if (role == Role.ADMIN) throw new BadRequestException("Cannot create admin users here");
        AuthService.validateNewPassword(req.password());
        return ApiDtos.UserDto.from(authService.createPrivilegedUser(
                req.firstName(), req.lastName(), req.email(), req.phone(), req.password(), role));
    }

    @PutMapping("/users/{id}/access")
    @org.springframework.transaction.annotation.Transactional
    public ApiDtos.UserDto access(@PathVariable java.util.UUID id,
                                 @Valid @RequestBody RequestDtos.UpdateUserAccessRequest req) {
        com.habeshlist.entity.User user = users.findForUpdate(id)
                .orElseThrow(() -> new com.habeshlist.exception.NotFoundException("User not found"));
        if (user.getRole() == Role.ADMIN) throw new BadRequestException("Administrator access cannot be changed here");
        user.setActive(req.active());
        user.setCredentialVersion(user.getCredentialVersion() + 1);
        return ApiDtos.UserDto.from(users.save(user));
    }

    @GetMapping("/dashboard")
    public ApiDtos.AdminDashboardDto dashboard() {
        return referralService.adminStats();
    }

    @GetMapping("/agent-codes")
    public List<Map<String, String>> agentCodes() {
        return agentProfiles.findAll().stream()
                .map(a -> Map.of(
                        "name", a.getUser().getFirstName() + " " + a.getUser().getLastName(),
                        "referralCode", a.getReferralCode() == null ? "" : a.getReferralCode()))
                .toList();
    }

    @GetMapping("/users")
    public List<ApiDtos.UserDto> listUsers(@RequestParam(required = false) String role) {
        if (role == null) return users.findAll().stream().map(ApiDtos.UserDto::from).toList();
        return users.findAllByRole(Role.valueOf(role.toUpperCase())).stream().map(ApiDtos.UserDto::from).toList();
    }
}
