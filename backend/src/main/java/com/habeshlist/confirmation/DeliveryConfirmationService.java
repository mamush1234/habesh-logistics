package com.habeshlist.confirmation;

import com.habeshlist.confirmation.ConfirmationDtos.*;
import com.habeshlist.entity.DeliveryRequest;
import com.habeshlist.entity.enums.MarketplaceTypes.*;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.*;
import com.habeshlist.repository.DeliveryRequestRepository;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.service.MarketplaceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class DeliveryConfirmationService {
    private static final int MAX_ATTEMPTS = 5;
    private final DeliveryRequestRepository packages;
    private final DeliveryConfirmationRepository confirmations;
    private final MarketplaceService marketplace;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public DeliveryConfirmationService(DeliveryRequestRepository packages, DeliveryConfirmationRepository confirmations,
            MarketplaceService marketplace, PasswordEncoder passwords, Clock clock) {
        this.packages = packages;
        this.confirmations = confirmations;
        this.marketplace = marketplace;
        this.passwords = passwords;
        this.clock = clock;
    }

    private boolean sender(DeliveryRequest p) {
        return p.getSender() != null && p.getSender().getId().equals(SecurityUtils.currentUser().userId());
    }
    private boolean verifier(DeliveryRequest p) {
        Role role = SecurityUtils.currentUser().role();
        return role == Role.ADMIN || (role == Role.AGENT && p.getAcceptedOffer() != null
                && p.getAcceptedOffer().getTraveler().getId().equals(SecurityUtils.currentUser().userId()));
    }
    private void access(DeliveryRequest p) {
        boolean carrier = p.getAcceptedOffer() != null
                && p.getAcceptedOffer().getTraveler().getId().equals(SecurityUtils.currentUser().userId());
        if (!sender(p) && !carrier && SecurityUtils.currentUser().role() != Role.ADMIN)
            throw new AccessDeniedException("Only booking participants and admins can view delivery confirmation");
        if (SecurityUtils.currentUser().role() == Role.TRAVELER && !p.isAdminApproved())
            throw new AccessDeniedException("This request is awaiting administrator approval");
    }
    private boolean eligible(DeliveryRequest p) {
        return p.isDeliveryCodeRequired() && p.getSender() != null && p.getAcceptedOffer() != null
                && p.getStatus() == DeliveryStatus.ARRIVED
                && (p.getEscrowStatus() == EscrowStatus.DEMO_HELD || p.getEscrowStatus() == EscrowStatus.STRIPE_TEST_HELD);
    }
    private Instant retryAt(DeliveryConfirmation c) {
        Instant retry = c.getIssuedAt().plusSeconds(60);
        if (c.getIssueCount() >= 5 && c.getIssueWindowAt().plusSeconds(3600).isAfter(retry))
            retry = c.getIssueWindowAt().plusSeconds(3600);
        if (c.getFailedAttempts() >= MAX_ATTEMPTS && c.getExpiresAt().isAfter(retry)) retry = c.getExpiresAt();
        return retry;
    }

    @Transactional(readOnly = true)
    public Status status(UUID id) {
        DeliveryRequest p = packages.findById(id).orElseThrow(() -> new NotFoundException("Package not found"));
        access(p);
        DeliveryConfirmation c = confirmations.findById(id).orElse(null);
        Instant now = clock.instant();
        Instant retry = c == null ? null : retryAt(c);
        boolean active = eligible(p) && (c == null || c.getConfirmedAt() == null);
        return new Status(p.isDeliveryCodeRequired() && p.getSender() != null,
                active && sender(p) && (retry == null || !now.isBefore(retry)),
                active && verifier(p) && c != null && now.isBefore(c.getExpiresAt()) && c.getFailedAttempts() < MAX_ATTEMPTS,
                c == null ? null : c.getExpiresAt(), c == null ? null : c.getConfirmedAt(),
                c == null ? MAX_ATTEMPTS : MAX_ATTEMPTS - c.getFailedAttempts(), retry);
    }

    @Transactional
    public Issued issue(UUID id) {
        DeliveryRequest p = packages.findForUpdate(id).orElseThrow(() -> new NotFoundException("Package not found"));
        if (!sender(p)) throw new AccessDeniedException("Only the owning sender can reveal a code for manual sharing");
        if (!eligible(p)) throw new BadRequestException("A funded booking must have arrived before issuing a delivery code");
        Instant now = clock.instant();
        DeliveryConfirmation c = confirmations.findById(id).orElse(null);
        if (c != null && c.getConfirmedAt() != null) throw new BadRequestException("Delivery already confirmed");
        if (c != null && now.isBefore(retryAt(c)))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait before issuing another delivery code");
        if (c == null) {
            c = new DeliveryConfirmation();
            c.setPackageId(id);
            c.setIssueWindowAt(now);
        }
        if (!now.isBefore(c.getIssueWindowAt().plusSeconds(3600))) {
            c.setIssueWindowAt(now);
            c.setIssueCount(0);
        }
        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (c.getCodeHash() != null && passwords.matches(code, c.getCodeHash()));
        c.setCodeHash(passwords.encode(code));
        c.setIssuedAt(now);
        c.setExpiresAt(now.plusSeconds(900));
        c.setFailedAttempts(0);
        c.setIssueCount(c.getIssueCount() + 1);
        confirmations.save(c);
        return new Issued(code, c.getExpiresAt());
    }

    static class RejectedCode extends BadRequestException {
        RejectedCode(String message) { super(message); }
    }

    @Transactional(noRollbackFor = RejectedCode.class)
    public Status verify(UUID id, Input input) {
        DeliveryRequest p = packages.findForUpdate(id).orElseThrow(() -> new NotFoundException("Package not found"));
        if (!verifier(p)) throw new AccessDeniedException("Only the selected agent or an admin can enter the receiver code");
        if (!eligible(p)) throw new BadRequestException("Delivery code verification requires a funded, arrived booking");
        DeliveryConfirmation c = confirmations.findById(id)
                .orElseThrow(() -> new BadRequestException("Ask the sender to issue and share a delivery code with the receiver"));
        if (c.getConfirmedAt() != null) throw new BadRequestException("Delivery code has already been used");
        if (!clock.instant().isBefore(c.getExpiresAt())) throw new BadRequestException("Delivery code expired; ask the sender for a new code");
        if (c.getFailedAttempts() >= MAX_ATTEMPTS) throw new BadRequestException("Delivery code locked after five incorrect attempts; wait for expiry and ask for a new code");
        if (!passwords.matches(input.code(), c.getCodeHash())) {
            c.setFailedAttempts(c.getFailedAttempts() + 1);
            throw new RejectedCode(c.getFailedAttempts() == MAX_ATTEMPTS
                    ? "Delivery code locked after five incorrect attempts"
                    : "Incorrect delivery code. Attempts remaining: " + (MAX_ATTEMPTS - c.getFailedAttempts()));
        }
        c.setConfirmedAt(clock.instant());
        c.setConfirmedBy(SecurityUtils.currentUser().userId());
        confirmations.saveAndFlush(c);
        marketplace.completeVerifiedDelivery(id);
        return status(id);
    }
}
