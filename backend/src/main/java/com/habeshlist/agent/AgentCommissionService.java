package com.habeshlist.agent;

import com.habeshlist.entity.DeliveryRequest;
import com.habeshlist.entity.enums.CommissionStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import com.habeshlist.repository.AgentProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Optional;

@Service
public class AgentCommissionService {
    private final AgentCommissionRepository commissions;
    private final AgentProfileRepository profiles;
    private final com.habeshlist.admin.PlatformSettingsRepository platformSettings;
    private final Clock clock;
    private final BigDecimal defaultRate;

    public AgentCommissionService(AgentCommissionRepository commissions, AgentProfileRepository profiles,
            com.habeshlist.admin.PlatformSettingsRepository platformSettings, Clock clock,
            @Value("${app.pricing.default-commission-rate:0.10}") BigDecimal defaultRate) {
        this.commissions = commissions;
        this.profiles = profiles;
        this.platformSettings = platformSettings;
        this.clock = clock;
        this.defaultRate = defaultRate;
    }

    public static BigDecimal calculate(BigDecimal rate, BigDecimal deliveryPrice) {
        if (rate == null || deliveryPrice == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0
                || deliveryPrice.signum() < 0)
            throw new IllegalArgumentException("Commission rate must be 0..1 and price must be non-negative");
        return deliveryPrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /** Agent-specific rate first, then the admin-managed platform default, then the configured fallback. */
    public BigDecimal rateFor(java.util.UUID agentId) {
        return profiles.findById(agentId).map(p -> p.getCommissionRate()).filter(r -> r != null)
                .or(() -> platformSettings.findById(com.habeshlist.admin.PlatformSettings.SINGLETON_ID)
                        .map(s -> s.getAgentCommissionPercent().movePointLeft(2)))
                .orElse(defaultRate);
    }

    /** Creates one PENDING commission when an agent-created delivery completes; repeated calls are no-ops. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AgentCommission> recordCompletedDelivery(DeliveryRequest request) {
        if (request.getCreatedByAgent() == null || request.getStatus() != DeliveryStatus.COMPLETED
                || request.getAcceptedOffer() == null || commissions.existsByRequestId(request.getId()))
            return Optional.empty();
        BigDecimal rate = rateFor(request.getCreatedByAgent().getId());
        BigDecimal base = request.getAcceptedOffer().getPrice();
        return Optional.of(commissions.save(AgentCommission.builder().agent(request.getCreatedByAgent())
                .customer(request.getSender()).request(request).rate(rate)
                .baseAmount(base.setScale(2, RoundingMode.HALF_UP)).amount(calculate(rate, base))
                .status(CommissionStatus.PENDING).createdAt(clock.instant()).build()));
    }
}
