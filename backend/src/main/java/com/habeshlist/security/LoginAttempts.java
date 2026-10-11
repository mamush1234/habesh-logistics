package com.habeshlist.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class LoginAttempts {
    private record Attempt(int count, Instant expiresAt) {}
    private final Map<String, Attempt> attempts = new HashMap<>();

    public synchronized void check(String email) {
        Instant now = Instant.now();
        attempts.values().removeIf(attempt -> !attempt.expiresAt().isAfter(now));
        Attempt attempt = attempts.get(key(email));
        if (attempt != null && attempt.count() >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again later.");
        }
        if (attempt != null) {
            attempts.put(key(email), new Attempt(attempt.count() + 1, attempt.expiresAt()));
        } else {
            attempts.put(key(email), new Attempt(1, now.plus(15, ChronoUnit.MINUTES)));
        }
    }

    public synchronized void succeeded(String email) { attempts.remove(key(email)); }

    private static String key(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
