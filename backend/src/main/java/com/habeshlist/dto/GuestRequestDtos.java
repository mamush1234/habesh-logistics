package com.habeshlist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class GuestRequestDtos {
    private GuestRequestDtos() {}
    public record CreateLinkInput(@NotBlank @Size(max = 120) String label) {}
    public record TokenInput(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {}
    public record SubmitInput(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "(?=[+0-9 ()\\-\\.]{7,40}$)(?:\\D*\\d){7,15}\\D*") String phone,
            @NotBlank @Size(max = 3_000_000) String photoDataUrl,
            @NotNull @Valid MarketplaceDtos.RequestInput request) {}
    public record LinkDto(UUID id, String label, Instant createdAt, Instant expiresAt, String status, UUID requestId) {}
    public record CreatedLinkDto(LinkDto link, String token) {}
    public record GuestStatusDto(String status, String message) {}
    public record ConvertInput(
            @NotNull @Valid com.habeshlist.dto.request.RequestDtos.RecipientRequest recipient,
            @NotBlank @Size(max = 100) String destinationCountry,
            @NotNull com.habeshlist.entity.enums.PickupMethod pickupMethod,
            @Size(max = 500) String pickupAddress,
            @FutureOrPresent java.time.LocalDate pickupRequestedDate,
            @Size(max = 100) String pickupTimeWindow) {}
}
