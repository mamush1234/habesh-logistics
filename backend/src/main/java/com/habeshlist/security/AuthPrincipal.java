package com.habeshlist.security;

import com.habeshlist.entity.enums.Role;
import java.util.UUID;

public record AuthPrincipal(UUID userId, String email, Role role) {}
