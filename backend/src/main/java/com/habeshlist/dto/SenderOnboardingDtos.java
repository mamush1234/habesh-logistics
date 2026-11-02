package com.habeshlist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class SenderOnboardingDtos {
    private SenderOnboardingDtos() {}

    public record Input(
            @NotBlank @Size(max = 255) String firstName,
            @NotBlank @Size(max = 255) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 40) @Pattern(regexp = "(?=(?:\\D*\\d){7})\\+?[0-9 ()-]{7,40}") String phone,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotNull @Valid MarketplaceDtos.RequestInput packageDetails) {}

    public record Result(ApiDtos.AuthResponse auth, UUID requestId) {}
}
