package com.habeshlist;

import com.habeshlist.payment.StripeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class StripeGatewayTest {
    @Test
    void onlyCompleteTestConfigurationIsAccepted() {
        assertTrue(new StripeGateway("sk_test_fixture", "pk_test_fixture", "whsec_fixture").configured());
        for (StripeGateway gateway : new StripeGateway[] {
                new StripeGateway("", "", ""),
                new StripeGateway("sk_live_fixture", "pk_live_fixture", "whsec_fixture"),
                new StripeGateway("sk_test_fixture", "pk_live_fixture", "whsec_fixture"),
                new StripeGateway("sk_live_fixture", "pk_test_fixture", "whsec_fixture"),
                new StripeGateway("sk_test_fixture", "pk_test_fixture", "")
        }) {
            assertFalse(gateway.configured());
            assertEquals(503, assertThrows(ResponseStatusException.class, gateway::publishableKey).getStatusCode().value());
            assertThrows(ResponseStatusException.class, gateway::webhookSecret);
        }
    }
}
