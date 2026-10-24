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
            jdbc.execute("CREATE TABLE shipments (id uuid PRIMARY KEY, sender_id uuid REFERENCES users(id))");
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
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V13__delivery_chat.sql")).execute(source);
            UUID requestId = UUID.randomUUID();
            jdbc.update("INSERT INTO delivery_requests (id,sender_id,pickup_city,destination_city,available_from,deadline,item_type,description,weight_kg,offered_price,status,escrow_status,created_at,updated_at) VALUES (?,?,'A','B',CURRENT_DATE,CURRENT_DATE,'DOCUMENTS','Test',1,20,'REQUESTED','NONE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", requestId, userId);
            jdbc.update("INSERT INTO delivery_messages VALUES (?,?,?,'Persisted message',CURRENT_TIMESTAMP,1)", UUID.randomUUID(), requestId, userId);
            assertEquals("Persisted message", jdbc.queryForObject("SELECT body FROM delivery_messages WHERE request_id = ?", String.class, requestId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO delivery_messages VALUES (?,?,?,'Duplicate',CURRENT_TIMESTAMP,1)", UUID.randomUUID(), requestId, userId));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM market_trips", Integer.class));
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V14__guest_sender_requests.sql")).execute(source);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM delivery_messages", Integer.class));
            UUID guestId = UUID.randomUUID();
            jdbc.update("INSERT INTO delivery_requests (id,sender_id,guest_name,guest_email,guest_phone,product_photo,pickup_city,destination_city,available_from,deadline,item_type,description,weight_kg,offered_price,status,escrow_status,created_at,updated_at) VALUES (?,NULL,'Guest','guest@example.com','1234567890','photo','A','B',CURRENT_DATE,CURRENT_DATE,'DOCUMENTS','Guest parcel',1,20,'REQUESTED','NONE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", guestId);
            UUID linkId = UUID.randomUUID();
            jdbc.update("INSERT INTO guest_request_links (id,token_hash,label,created_by_id,created_at,expires_at,request_id) VALUES (?,'hash','Guest',?,CURRENT_TIMESTAMP,DATEADD('DAY',7,CURRENT_TIMESTAMP),?)", linkId, userId, guestId);
            assertEquals(guestId, jdbc.queryForObject("SELECT request_id FROM guest_request_links WHERE id = ?", UUID.class, linkId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE delivery_requests SET guest_email = NULL WHERE id = ?", guestId));
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V15__guest_request_shipments.sql")).execute(source);
            UUID shipmentId = UUID.randomUUID();
            jdbc.update("INSERT INTO shipments (id,source_request_id) VALUES (?,?)", shipmentId, guestId);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO shipments (id,source_request_id) VALUES (?,?)", UUID.randomUUID(), guestId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO shipments (id) VALUES (?)", UUID.randomUUID()));
            assertEquals(guestId, jdbc.queryForObject("SELECT source_request_id FROM shipments WHERE id = ?", UUID.class, shipmentId));
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V16__guest_sender_accounts.sql")).execute(source);
            assertFalse(jdbc.queryForObject("SELECT must_change_password FROM users WHERE id = ?", Boolean.class, userId));
            assertNull(jdbc.queryForObject("SELECT guest_account_id FROM delivery_requests WHERE id = ?", UUID.class, guestId));
            jdbc.update("UPDATE delivery_requests SET guest_account_id = ? WHERE id = ?", userId, guestId);
            assertEquals(userId, jdbc.queryForObject("SELECT guest_account_id FROM delivery_requests WHERE id = ?", UUID.class, guestId));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM delivery_messages", Integer.class));
        } catch (java.sql.SQLException ex) {
            throw new AssertionError("Migration connection failed", ex);
        }
    }
}
