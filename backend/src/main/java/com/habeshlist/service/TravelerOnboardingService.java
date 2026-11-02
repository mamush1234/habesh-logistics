package com.habeshlist.service;

import com.habeshlist.dto.TravelerOnboardingDtos.*;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.repository.TravelerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
public class TravelerOnboardingService {
    private final AuthService auth;
    private final MarketplaceService marketplace;
    private final TravelerProfileRepository profiles;
    private final Clock clock;

    public TravelerOnboardingService(AuthService auth, MarketplaceService marketplace,
                                     TravelerProfileRepository profiles, Clock clock) {
        this.auth = auth;
        this.marketplace = marketplace;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public Result register(Input input) {
        if (!Boolean.TRUE.equals(input.termsAccepted()))
            throw new com.habeshlist.exception.BadRequestException("Accept the traveler community terms");
        if (input.tripDetails().expectedArrivalDate() == null)
            throw new com.habeshlist.exception.BadRequestException("An arrival date is required for traveler signup");
        AuthService.validateNewPassword(input.password());
        var traveler = auth.createPrivilegedUser(input.firstName().trim(), input.lastName().trim(),
                input.email().trim(), input.phone().trim(), input.password(), Role.TRAVELER);
        var profile = profiles.findById(traveler.getId()).orElseThrow();
        profile.setTermsAcceptedAt(clock.instant());
        profile.setTermsVersion("traveler-community-v1");
        var trip = marketplace.postTravelerTrip(traveler, input.tripDetails());
        return new Result(auth.response(traveler), trip.getId());
    }
}
