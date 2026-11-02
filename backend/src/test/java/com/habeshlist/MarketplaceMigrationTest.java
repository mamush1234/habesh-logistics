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
    void settingsAuditRelationshipUsesTheMigratedColumn() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:settings-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
        var jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            jdbc.execute("CREATE TABLE users (id uuid PRIMARY KEY)");
            UUID admin = UUID.randomUUID();
            jdbc.update("INSERT INTO users VALUES (?)", admin);
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V19__platform_settings.sql")).execute(source);
            var column = com.habeshlist.admin.PlatformSettings.class.getDeclaredField("updatedBy")
                    .getAnnotation(jakarta.persistence.JoinColumn.class);
            assertNotNull(column, "The audit relationship must explicitly map the existing migration column");
            assertEquals("updated_by", column.name());
            jdbc.update("UPDATE platform_settings SET " + column.name() + " = ? WHERE id = 1", admin);
            assertEquals(admin, jdbc.queryForObject("SELECT updated_by FROM platform_settings WHERE id = 1", UUID.class));
        }
    }

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
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V17__sender_request_admin_approval.sql")).execute(source);
            assertFalse(jdbc.queryForObject("SELECT admin_approved FROM delivery_requests WHERE id = ?", Boolean.class, requestId));
            assertFalse(jdbc.queryForObject("SELECT admin_approved FROM delivery_requests WHERE id = ?", Boolean.class, guestId));
            jdbc.update("UPDATE delivery_requests SET admin_approved = true WHERE id = ?", requestId);
            assertTrue(jdbc.queryForObject("SELECT admin_approved FROM delivery_requests WHERE id = ?", Boolean.class, requestId));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM delivery_messages", Integer.class));
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V18__agent_portal.sql")).execute(source);
            assertNull(jdbc.queryForObject("SELECT created_by_agent_id FROM delivery_requests WHERE id = ?", UUID.class, requestId));
            assertNull(jdbc.queryForObject("SELECT created_by_agent_id FROM market_trips WHERE id = ?", UUID.class, tripId));
            UUID agentId = UUID.randomUUID();
            jdbc.update("INSERT INTO users (id) VALUES (?)", agentId);
            jdbc.update("INSERT INTO agent_customers VALUES (?,?,?,'SENDER',CURRENT_TIMESTAMP)", UUID.randomUUID(), agentId, userId);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO agent_customers VALUES (?,?,?,'SENDER',CURRENT_TIMESTAMP)", UUID.randomUUID(), agentId, userId));
            jdbc.update("INSERT INTO agent_commissions (id,agent_id,customer_id,request_id,rate,base_amount,amount,status,created_at) VALUES (?,?,?,?,0.1,40,4,'PENDING',CURRENT_TIMESTAMP)", UUID.randomUUID(), agentId, userId, requestId);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO agent_commissions (id,agent_id,customer_id,request_id,rate,base_amount,amount,status,created_at) VALUES (?,?,?,?,0.1,40,4,'PENDING',CURRENT_TIMESTAMP)", UUID.randomUUID(), agentId, userId, requestId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO agent_authorizations (id,agent_id,customer_id,status,method,granted_at,expires_at) VALUES (?,?,?,'ACTIVE','PHONE',CURRENT_TIMESTAMP,DATEADD('DAY',-1,CURRENT_TIMESTAMP))", UUID.randomUUID(), agentId, userId));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM delivery_requests", Integer.class));
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V22__trip_prices_and_handoff.sql")).execute(source);
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V23__targeted_shipment_requests.sql")).execute(source);
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM delivery_requests", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM shipment_requests", Integer.class));
            UUID targetedId = UUID.randomUUID();
            String insert = "INSERT INTO shipment_requests (id,package_id,trip_id,sender_id,status,pending_package_id,quote_json,created_at,expires_at) VALUES (?,?,?,?,'PENDING',?,'{}',CURRENT_TIMESTAMP,DATEADD('HOUR',1,CURRENT_TIMESTAMP))";
            jdbc.update(insert, targetedId, requestId, tripId, userId, requestId);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update(insert, UUID.randomUUID(), requestId, tripId, userId, requestId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update(insert, UUID.randomUUID(), guestId, tripId, userId, null));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE shipment_requests SET status = 'EXPIRED' WHERE id = ?", targetedId));
            jdbc.update("UPDATE shipment_requests SET status = 'EXPIRED', pending_package_id = NULL WHERE id = ?", targetedId);
            jdbc.update(insert, UUID.randomUUID(), requestId, tripId, userId, requestId);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE shipment_requests SET expires_at = created_at WHERE id = ?", targetedId));
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__shipment_request_decisions.sql")).execute(source);
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM shipment_requests", Integer.class));
            assertNull(jdbc.queryForObject("SELECT proposed_quote_json FROM shipment_requests WHERE id = ?", String.class, targetedId));
            assertNull(jdbc.queryForObject("SELECT decided_at FROM shipment_requests WHERE id = ?", java.sql.Timestamp.class, targetedId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE shipment_requests SET accepted_offer_id = ? WHERE id = ?", UUID.randomUUID(), targetedId));
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V25__stripe_test_payments.sql")).execute(source);
            UUID paymentId = UUID.randomUUID();
            jdbc.update("INSERT INTO stripe_payments (id,package_id,shipment_request_id,status,amount_cents,quote_json,created_at,updated_at) VALUES (?,?,?,'PENDING',1430,'{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    paymentId, requestId, targetedId);
            assertEquals(1430L, jdbc.queryForObject("SELECT amount_cents FROM stripe_payments WHERE id = ?", Long.class, paymentId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE stripe_payments SET amount_cents = 1 WHERE id = ?", paymentId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE stripe_payments SET package_id = ? WHERE id = ?", UUID.randomUUID(), paymentId));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM shipment_requests", Integer.class));
            assertNull(jdbc.queryForObject("SELECT handoff_location FROM market_trips WHERE id = ?", String.class, tripId));
            assertNull(jdbc.queryForObject("SELECT notes FROM market_trips WHERE id = ?", String.class, tripId));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM trip_item_prices", Integer.class));
            UUID priceId = UUID.randomUUID();
            jdbc.update("INSERT INTO trip_item_prices VALUES (?,?,'CLOTHING',2.50,'PER_KG')", priceId, tripId);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO trip_item_prices VALUES (?,?,'CLOTHING',3,'PER_ITEM')", UUID.randomUUID(), tripId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE trip_item_prices SET price = 0 WHERE id = ?", priceId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE trip_item_prices SET unit = 'INVALID' WHERE id = ?", priceId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE trip_item_prices SET trip_id = ? WHERE id = ?", UUID.randomUUID(), priceId));
            assertEquals(4, jdbc.queryForObject("SELECT reserved_kg FROM market_trips WHERE id = ?", Integer.class, tripId));
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V19__platform_settings.sql")).execute(source);
            assertEquals("SENDER", jdbc.queryForObject("SELECT fee_payer FROM platform_settings WHERE id = 1", String.class));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO platform_settings (id,platform_fee_percent,agent_commission_percent,fee_payer,updated_at) VALUES (2,5,5,'SENDER',CURRENT_TIMESTAMP)"));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE platform_settings SET platform_fee_percent = 101 WHERE id = 1"));
            jdbc.execute("CREATE TABLE recipients (id uuid PRIMARY KEY)");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V20__package_items_and_receiver.sql")).execute(source);
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM package_items", Integer.class));
            assertEquals("DOCUMENTS", jdbc.queryForObject("SELECT category FROM package_items WHERE request_id = ?", String.class, requestId));
            assertEquals(1, jdbc.queryForObject("SELECT weight_kg FROM package_items WHERE request_id = ?", Integer.class, requestId));
            assertNull(jdbc.queryForObject("SELECT receiver_id FROM delivery_requests WHERE id = ?", UUID.class, requestId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE package_items SET weight_kg = 0 WHERE request_id = ?", requestId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE delivery_requests SET receiver_id = ? WHERE id = ?", UUID.randomUUID(), requestId));
            jdbc.update("UPDATE traveler_profiles SET marketplace_verification = 'FULLY_VERIFIED' WHERE user_id = ?", userId);
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V21__traveler_onboarding_terms.sql")).execute(source);
            assertNull(jdbc.queryForObject("SELECT terms_accepted_at FROM traveler_profiles WHERE user_id = ?", java.sql.Timestamp.class, userId));
            assertNull(jdbc.queryForObject("SELECT terms_version FROM traveler_profiles WHERE user_id = ?", String.class, userId));
            assertEquals("FULLY_VERIFIED", jdbc.queryForObject("SELECT marketplace_verification FROM traveler_profiles WHERE user_id = ?", String.class, userId));
            jdbc.update("UPDATE traveler_profiles SET terms_accepted_at = CURRENT_TIMESTAMP, terms_version = 'traveler-community-v1' WHERE user_id = ?", userId);
            assertNotNull(jdbc.queryForObject("SELECT terms_accepted_at FROM traveler_profiles WHERE user_id = ?", java.sql.Timestamp.class, userId));
            assertEquals("traveler-community-v1", jdbc.queryForObject("SELECT terms_version FROM traveler_profiles WHERE user_id = ?", String.class, userId));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM delivery_requests", Integer.class));
            UUID legacyOffer = UUID.randomUUID();
            jdbc.update("INSERT INTO delivery_offers (id,request_id,trip_id,traveler_id,price,status,created_at) VALUES (?,?,?,?,20,'ACCEPTED',CURRENT_TIMESTAMP)",
                    legacyOffer, requestId, tripId, agentId);
            jdbc.update("UPDATE delivery_requests SET accepted_offer_id = ?, rating = 4 WHERE id = ?", legacyOffer, requestId);
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V26__delivery_reviews.sql")).execute(source);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM delivery_reviews", Integer.class));
            assertEquals(4, jdbc.queryForObject("SELECT rating FROM delivery_reviews WHERE package_id = ?", Integer.class, requestId));
            assertTrue(jdbc.queryForObject("SELECT legacy FROM delivery_reviews WHERE package_id = ?", Boolean.class, requestId));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM delivery_review_scores", Integer.class));
            assertEquals(4, jdbc.queryForObject("SELECT rating FROM delivery_requests WHERE id = ?", Integer.class, requestId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO delivery_reviews (id,package_id,author_id,recipient_id,direction,rating,legacy,created_at) VALUES (?,?,?,?,'SENDER_TO_CARRIER',5,false,CURRENT_TIMESTAMP)",
                            UUID.randomUUID(), requestId, userId, agentId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE delivery_reviews SET rating = 6 WHERE package_id = ?", requestId));
            jdbc.update("INSERT INTO delivery_review_scores VALUES (?,'COMMUNICATION',5)", requestId);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE delivery_review_scores SET score = 0 WHERE review_id = ?", requestId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE delivery_review_scores SET criterion = 'UNKNOWN' WHERE review_id = ?", requestId));
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V27__delivery_confirmation.sql")).execute(source);
            assertFalse(jdbc.queryForObject("SELECT delivery_code_required FROM delivery_requests WHERE id = ?", Boolean.class, requestId));
            assertTrue(jdbc.queryForObject("SELECT column_default FROM information_schema.columns WHERE table_name = 'DELIVERY_REQUESTS' AND column_name = 'DELIVERY_CODE_REQUIRED'", String.class).equalsIgnoreCase("TRUE"));
            jdbc.update("INSERT INTO delivery_confirmations (package_id,code_hash,issued_at,expires_at,failed_attempts,issue_window_at,issue_count) VALUES (?,'test-hash',CURRENT_TIMESTAMP,DATEADD('MINUTE',15,CURRENT_TIMESTAMP),0,CURRENT_TIMESTAMP,1)", requestId);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE delivery_confirmations SET failed_attempts = 6 WHERE package_id = ?", requestId));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE delivery_confirmations SET confirmed_at = CURRENT_TIMESTAMP WHERE package_id = ?", requestId));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM delivery_messages", Integer.class));
        } catch (java.sql.SQLException ex) {
            throw new AssertionError("Migration connection failed", ex);
        }
    }
}
