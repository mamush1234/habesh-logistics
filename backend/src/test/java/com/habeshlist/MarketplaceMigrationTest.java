package com.habeshlist;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MarketplaceMigrationTest {
    @Test
    void migrationUpgradesLegacyTablesWithoutDeletingRecords() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
        var jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            jdbc.execute("CREATE TABLE users (id uuid PRIMARY KEY)");
            jdbc.execute("CREATE TABLE traveler_profiles (user_id uuid PRIMARY KEY REFERENCES users(id))");
            jdbc.execute("CREATE TABLE shipments (id uuid PRIMARY KEY)");
            jdbc.execute("CREATE TABLE package_notifications (id uuid PRIMARY KEY, shipment_id uuid NOT NULL REFERENCES shipments(id))");
            UUID userId = UUID.randomUUID();
            jdbc.update("INSERT INTO users VALUES (?)", userId);
            jdbc.update("INSERT INTO traveler_profiles VALUES (?)", userId);
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V12__delivery_marketplace.sql")).execute(source);
            assertEquals("UNVERIFIED", jdbc.queryForObject("SELECT marketplace_verification FROM traveler_profiles WHERE user_id = ?", String.class, userId));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM delivery_requests", Integer.class));
            UUID tripId = UUID.randomUUID();
            jdbc.update("INSERT INTO market_trips (id,traveler_id,origin,destination,departure_date,capacity_kg,reserved_kg,created_at) VALUES (?,?, 'A', 'B', CURRENT_DATE, 5, 4, CURRENT_TIMESTAMP)", tripId, userId);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE market_trips SET reserved_kg = 6 WHERE id = ?", tripId));
            jdbc.update("INSERT INTO package_notifications (id, shipment_id, request_id) VALUES (?, NULL, NULL)", UUID.randomUUID());
        } catch (java.sql.SQLException ex) {
            throw new AssertionError("Migration connection failed", ex);
        }
    }
}
