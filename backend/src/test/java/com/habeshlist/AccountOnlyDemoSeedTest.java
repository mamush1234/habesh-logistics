package com.habeshlist;

import com.habeshlist.service.DataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.demo-operational-data-enabled=false",
        "app.demo-marketplace-trips-enabled=true"
})
@ActiveProfiles("test")
class AccountOnlyDemoSeedTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSeeder seeder;

    @Test
    void freshDatabaseGetsOnlyAccountsAndRepeatedStartupDoesNotCreateSamples() {
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM users", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM sender_profiles", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM traveler_profiles", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM agent_profiles", Integer.class));
        for (int run = 0; run < 2; run++) {
            for (String table : java.util.List.of("shipments", "trips", "luggage", "recipients", "commissions",
                    "tracking_events", "market_trips", "delivery_requests", "package_notifications")) {
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class), table);
            }
            seeder.seed();
        }
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM users", Integer.class));
    }
}
