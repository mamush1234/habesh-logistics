package com.habeshlist.confirmation;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class ConfirmationDtos {
    private ConfirmationDtos() {}
    public record Status(boolean required, boolean canIssue, boolean canVerify, Instant expiresAt,
            Instant confirmedAt, int attemptsRemaining, Instant retryIssueAt) {}
    public record Issued(String code, Instant expiresAt) {}
    public record Input(@NotNull @Pattern(regexp = "[0-9]{6}") String code) {}
}
