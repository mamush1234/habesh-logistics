package com.habeshlist.service;

import com.habeshlist.dto.SenderOnboardingDtos.*;
import com.habeshlist.entity.enums.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SenderOnboardingService {
    private final AuthService auth;
    private final MarketplaceService marketplace;

    public SenderOnboardingService(AuthService auth, MarketplaceService marketplace) {
        this.auth = auth;
        this.marketplace = marketplace;
    }

    @Transactional
    public Result register(Input input) {
        AuthService.validateNewPassword(input.password());
        var sender = auth.createPrivilegedUser(input.firstName().trim(), input.lastName().trim(),
                input.email().trim(), input.phone().trim(), input.password(), Role.SENDER);
        var request = marketplace.postSenderRequest(sender, input.packageDetails());
        return new Result(auth.response(sender), request.getId());
    }
}
