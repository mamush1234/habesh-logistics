package com.habeshlist;

import com.fasterxml.jackson.databind.*;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.*;
import com.habeshlist.entity.enums.MarketplaceTypes.*;
import com.habeshlist.repository.*;
import com.habeshlist.review.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:delivery-reviews;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeliveryReviewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired DeliveryRequestRepository packages;
    @Autowired DeliveryOfferRepository offers;
    @Autowired MarketTripRepository trips;
    @Autowired DeliveryReviewRepository reviews;
    @Autowired PasswordEncoder passwords;
    record Actor(User user, String token) {}
    Actor sender, agent, traveler, stranger;
    String admin;

    @BeforeEach
    void setup() throws Exception {
        sender = actor(Role.SENDER);
        agent = actor(Role.AGENT);
        traveler = actor(Role.TRAVELER);
        stranger = actor(Role.SENDER);
        admin = login("admin@habeshlist.com", "Admin@123");
    }

    Actor actor(Role role) throws Exception {
        String unique = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.builder().firstName("Review").lastName(unique)
                .email(unique + "@example.com").passwordHash(passwords.encode("Review-password-2026"))
                .role(role).active(true).build());
        return new Actor(user, login(user.getEmail(), "Review-password-2026"));
    }
    String login(String email, String password) throws Exception {
        return ok("POST", "/api/auth/login", null, Map.of("email", email, "password", password)).get("token").asText();
    }
    ResultActions call(String method, String path, String token, Object body) throws Exception {
        var request = switch (method) { case "GET" -> get(path); case "PUT" -> put(path); default -> post(path); };
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    JsonNode ok(String method, String path, String token, Object body) throws Exception {
        return json.readTree(call(method, path, token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String path(UUID id) { return "/api/marketplace/requests/" + id; }
    String reviewPath(UUID id) { return path(id) + "/reviews"; }
    UUID booking(Actor carrier, DeliveryStatus status) {
        Instant now = Instant.now();
        MarketTrip trip = trips.saveAndFlush(MarketTrip.builder().traveler(carrier.user())
                .origin("Review-" + UUID.randomUUID()).destination("Addis Ababa")
                .departureDate(LocalDate.now().plusDays(3)).expectedArrivalDate(LocalDate.now().plusDays(4))
                .capacityKg(new BigDecimal("5")).reservedKg(BigDecimal.ONE)
                .allowedItems(Set.of(ItemType.CLOTHING)).createdAt(now).build());
        DeliveryRequest p = packages.saveAndFlush(DeliveryRequest.builder().sender(sender.user())
                .pickupCity(trip.getOrigin()).destinationCity(trip.getDestination())
                .availableFrom(LocalDate.now()).deadline(LocalDate.now().plusDays(5))
                .itemType(ItemType.CLOTHING).description("Private parcel description")
                .weightKg(BigDecimal.ONE).offeredPrice(BigDecimal.TEN)
                .status(status).escrowStatus(status == DeliveryStatus.COMPLETED ? EscrowStatus.DEMO_RELEASED : EscrowStatus.NONE)
                .createdAt(now).updatedAt(now).build());
        DeliveryOffer offer = offers.saveAndFlush(DeliveryOffer.builder().request(p).traveler(carrier.user()).trip(trip)
                .price(BigDecimal.TEN).status(OfferStatus.ACCEPTED).createdAt(now).build());
        p.setAcceptedOffer(offer);
        packages.saveAndFlush(p);
        return p.getId();
    }
    Map<String, Object> input(boolean fromSender) {
        return new HashMap<>(Map.of("rating", 4, "scores", fromSender
                ? Map.of("COMMUNICATION", 5, "ON_TIME_DELIVERY", 4, "PACKAGE_HANDLING", 3, "RELIABILITY", 4)
                : Map.of("COMMUNICATION", 4, "ACCURATE_DESCRIPTION", 5, "HANDOFF_TIMELINESS", 3, "PACKAGING_QUALITY", 4),
                "comment", " Careful handoff. Thank you! "));
    }
    Set<String> fields(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    @Test
    void senderAndAgentReviewEachOtherOnceWithPrivateScoresAndNoDoubleCountInReputation() throws Exception {
        UUID id = booking(agent, DeliveryStatus.COMPLETED);
        JsonNode pending = ok("GET", reviewPath(id), sender.token(), null);
        assertTrue(pending.get("canReview").asBoolean());
        JsonNode saved = ok("POST", reviewPath(id), sender.token(), input(true));
        assertFalse(saved.get("canReview").asBoolean());
        JsonNode review = saved.get("reviews").get(0);
        assertEquals(Set.of("id", "direction", "rating", "scores", "comment", "legacy", "createdAt", "mine"), fields(review));
        assertEquals("Careful handoff. Thank you!", review.get("comment").asText());
        assertEquals(4, review.get("scores").size());
        assertTrue(review.get("mine").asBoolean());
        assertEquals(4, packages.findById(id).orElseThrow().getRating());
        assertEquals(4.0, packages.averageRating(agent.user().getId()));
        assertEquals(1, packages.ratingCount(agent.user().getId()));
        JsonNode reciprocal = ok("POST", reviewPath(id), agent.token(), input(false));
        assertEquals(2, reciprocal.get("reviews").size());
        assertFalse(reciprocal.get("canReview").asBoolean());
        assertEquals(1, packages.ratingCount(agent.user().getId()));
        assertEquals(0, packages.ratingCount(sender.user().getId()));
        for (Actor actor : List.of(sender, agent))
            call("POST", reviewPath(id), actor.token(), input(actor == sender)).andExpect(status().isBadRequest());
        call("POST", path(id) + "/rating", sender.token(), Map.of("rating", 5)).andExpect(status().isBadRequest());
        assertEquals(2, reviews.findByPackageRequestIdOrderByCreatedAtAscIdAsc(id).size());
        JsonNode publicTrip = ok("GET", "/api/public/travel", null, null).findParents("origin").stream()
                .filter(row -> row.get("origin").equals(pending.get("origin"))).findFirst().orElseThrow();
        assertEquals(4.0, publicTrip.get("rating").asDouble());
        assertEquals(1, publicTrip.get("ratingCount").asInt());
        assertFalse(publicTrip.toString().contains("Careful handoff"));
        assertFalse(publicTrip.toString().contains(agent.user().getEmail()));
        assertFalse(publicTrip.toString().contains("scores"));
        assertEquals(2, ok("GET", reviewPath(id), admin, null).get("reviews").size());
        assertFalse(ok("GET", reviewPath(id), admin, null).get("canReview").asBoolean());
        assertTrue(ok("GET", "/api/admin/console/requests?view=reviews&q=" + id, admin, null)
                .toString().contains(id.toString()));
    }

    @Test
    void reviewsRejectAnonymousOutsidersAndAdminAuthorshipEvenForApprovedPackages() throws Exception {
        UUID id = booking(agent, DeliveryStatus.COMPLETED);
        DeliveryRequest p = packages.findById(id).orElseThrow();
        p.setAdminApproved(true);
        packages.saveAndFlush(p);
        call("GET", reviewPath(id), null, null).andExpect(status().isUnauthorized());
        call("POST", reviewPath(id), null, input(true)).andExpect(status().isUnauthorized());
        for (Actor outsider : List.of(stranger, traveler)) {
            call("GET", reviewPath(id), outsider.token(), null).andExpect(status().isForbidden());
            call("POST", reviewPath(id), outsider.token(), input(true)).andExpect(status().isForbidden());
        }
        call("POST", reviewPath(id), admin, input(true)).andExpect(status().isForbidden());
        assertEquals(0, reviews.findByPackageRequestIdOrderByCreatedAtAscIdAsc(id).size());
    }

    @Test
    void travelerReviewExceptionDoesNotExposeUnapprovedPackageOrEnableOtherMutations() throws Exception {
        UUID unfinished = booking(traveler, DeliveryStatus.MATCHED);
        call("GET", reviewPath(unfinished), traveler.token(), null).andExpect(status().isForbidden());
        UUID id = booking(traveler, DeliveryStatus.COMPLETED);
        call("GET", path(id), traveler.token(), null).andExpect(status().isForbidden());
        JsonNode inbox = ok("GET", "/api/marketplace/reviews", traveler.token(), null);
        assertEquals(1, inbox.get("items").size());
        JsonNode row = inbox.get("items").get(0);
        assertEquals(id.toString(), row.get("packageId").asText());
        assertEquals(Set.of("packageId", "origin", "destination", "status", "canReview", "direction", "reviews"), fields(row));
        assertTrue(row.get("canReview").asBoolean());
        assertFalse(inbox.toString().contains("Private parcel description"));
        assertFalse(inbox.toString().contains(sender.user().getEmail()));
        ok("POST", reviewPath(id), traveler.token(), input(false));
        JsonNode adminInbox = ok("GET", "/api/admin/console/requests?view=reviews&q=" + id, admin, null);
        assertEquals(1, adminInbox.get("items").size());
        assertTrue(adminInbox.get("items").get(0).get("rating").isNull());
        ok("POST", reviewPath(id), sender.token(), input(true));
        JsonNode saved = ok("GET", reviewPath(id), traveler.token(), null);
        assertEquals(2, saved.get("reviews").size());
        call("POST", path(id) + "/rating", traveler.token(), Map.of("rating", 5)).andExpect(status().isForbidden());
        call("PUT", path(id) + "/status", traveler.token(), Map.of("status", "COMPLETED")).andExpect(status().isForbidden());
        call("POST", path(id) + "/offers", traveler.token(), Map.of("tripId", UUID.randomUUID(), "price", 20)).andExpect(status().isForbidden());
        call("POST", path(id) + "/messages", traveler.token(), Map.of("body", "No write access")).andExpect(status().isForbidden());
        call("GET", "/api/marketplace/shipment-requests/inbox", traveler.token(), null).andExpect(status().isForbidden());
    }

    @Test
    void noReviewsBeforeConfirmedCompletionOrForCancelledDisputedOrGuestBookings() throws Exception {
        for (DeliveryStatus state : DeliveryStatus.values()) {
            if (state == DeliveryStatus.COMPLETED) continue;
            UUID id = booking(agent, state);
            assertFalse(ok("GET", reviewPath(id), sender.token(), null).get("canReview").asBoolean());
            call("POST", reviewPath(id), sender.token(), input(true)).andExpect(status().isBadRequest());
            call("POST", reviewPath(id), agent.token(), input(false)).andExpect(status().isBadRequest());
        }
        UUID guest = booking(agent, DeliveryStatus.COMPLETED);
        DeliveryRequest p = packages.findById(guest).orElseThrow();
        p.setGuestAccount(sender.user());
        p.setGuestName("Guest");
        p.setGuestEmail(sender.user().getEmail());
        p.setGuestPhone("1234567890");
        p.setSender(null);
        packages.saveAndFlush(p);
        call("POST", reviewPath(guest), agent.token(), input(false)).andExpect(status().isBadRequest());
        call("POST", reviewPath(guest), sender.token(), input(true)).andExpect(status().isForbidden());
    }

    @Test
    void validatesScoresCategorySetAndCommentBeforePersisting() throws Exception {
        UUID id = booking(agent, DeliveryStatus.COMPLETED);
        for (Object bad : List.of(0, 6, 1.5)) {
            var input = input(true);
            input.put("rating", bad);
            call("POST", reviewPath(id), sender.token(), input).andExpect(status().isBadRequest());
        }
        var missing = input(true);
        missing.remove("rating");
        call("POST", reviewPath(id), sender.token(), missing).andExpect(status().isBadRequest());
        for (Object bad : List.of(0, 6, 1.5, "invalid")) {
            var input = input(true);
            input.put("scores", Map.of("COMMUNICATION", bad, "ON_TIME_DELIVERY", 4, "PACKAGE_HANDLING", 4, "RELIABILITY", 4));
            call("POST", reviewPath(id), sender.token(), input).andExpect(status().isBadRequest());
        }
        call("POST", reviewPath(id), sender.token(), input(false)).andExpect(status().isBadRequest());
        call("POST", reviewPath(id), agent.token(), input(true)).andExpect(status().isBadRequest());
        var shortScores = input(true);
        shortScores.put("scores", Map.of("COMMUNICATION", 4));
        call("POST", reviewPath(id), sender.token(), shortScores).andExpect(status().isBadRequest());
        var unknown = input(true);
        unknown.put("scores", Map.of("COMMUNICATION", 4, "UNKNOWN", 4, "PACKAGE_HANDLING", 4, "RELIABILITY", 4));
        call("POST", reviewPath(id), sender.token(), unknown).andExpect(status().isBadRequest());
        var longComment = input(true);
        longComment.put("comment", "x".repeat(2001));
        call("POST", reviewPath(id), sender.token(), longComment).andExpect(status().isBadRequest());
        assertNull(packages.findById(id).orElseThrow().getRating());
        assertTrue(reviews.findByPackageRequestIdOrderByCreatedAtAscIdAsc(id).isEmpty());
    }

    @Test
    void legacyRatingWritesOverallOnlyReviewAndPreventsDetailedResubmission() throws Exception {
        UUID id = booking(agent, DeliveryStatus.COMPLETED);
        ok("POST", path(id) + "/rating", sender.token(), Map.of("rating", 5));
        JsonNode review = ok("GET", reviewPath(id), sender.token(), null).get("reviews").get(0);
        assertTrue(review.get("legacy").asBoolean());
        assertEquals(0, review.get("scores").size());
        assertTrue(review.get("comment").isNull());
        call("POST", reviewPath(id), sender.token(), input(true)).andExpect(status().isBadRequest());
        ok("POST", reviewPath(id), agent.token(), input(false));
        assertEquals(1, packages.ratingCount(agent.user().getId()));
        assertEquals(5.0, packages.averageRating(agent.user().getId()));
    }

    @Test
    void inboxIsPaginatedScopedAndRejectsInvalidPage() throws Exception {
        Set<String> own = new HashSet<>();
        for (int i = 0; i < 21; i++) own.add(booking(agent, DeliveryStatus.COMPLETED).toString());
        JsonNode first = ok("GET", "/api/marketplace/reviews", sender.token(), null);
        JsonNode second = ok("GET", "/api/marketplace/reviews?page=1", agent.token(), null);
        assertEquals(21, first.get("totalElements").intValue());
        assertEquals(2, first.get("totalPages").intValue());
        assertEquals(20, first.get("items").size());
        assertEquals(1, second.get("items").size());
        for (JsonNode row : first.get("items")) assertTrue(own.remove(row.get("packageId").asText()));
        assertTrue(own.remove(second.get("items").get(0).get("packageId").asText()));
        assertTrue(own.isEmpty());
        assertEquals(0, ok("GET", "/api/marketplace/reviews", stranger.token(), null).get("totalElements").intValue());
        call("GET", "/api/marketplace/reviews?page=-1", sender.token(), null).andExpect(status().isBadRequest());
        call("GET", "/api/marketplace/reviews", admin, null).andExpect(status().isForbidden());
    }

    @Test
    void concurrentSubmissionsSaveOnlyOneReviewAndLegacyRatingCannotBypassIt() throws Exception {
        UUID id = booking(agent, DeliveryStatus.COMPLETED);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> call("POST", reviewPath(id), sender.token(), input(true)).andReturn().getResponse().getStatus());
            var second = pool.submit(() -> call("POST", path(id) + "/rating", sender.token(), Map.of("rating", 5)).andReturn().getResponse().getStatus());
            assertEquals(Set.of(200, 400), Set.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)));
            assertEquals(1, reviews.findByPackageRequestIdOrderByCreatedAtAscIdAsc(id).size());
            assertEquals(1, packages.ratingCount(agent.user().getId()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void normalDeliveryLifecycleOpensDetailedReviewsOnlyAfterCodeConfirmation() throws Exception {
        UUID id = booking(agent, DeliveryStatus.MATCHED);
        ok("POST", path(id) + "/demo-escrow", sender.token(), null);
        for (String state : List.of("PICKED_UP", "IN_TRANSIT", "ARRIVED"))
            ok("PUT", path(id) + "/status", agent.token(), Map.of("status", state));
        call("POST", reviewPath(id), sender.token(), input(true)).andExpect(status().isBadRequest());
        String code = ok("POST", path(id) + "/delivery-confirmation/code", sender.token(), null).get("code").asText();
        ok("POST", path(id) + "/delivery-confirmation/verify", agent.token(), Map.of("code", code));
        assertTrue(ok("GET", reviewPath(id), sender.token(), null).get("canReview").asBoolean());
        ok("POST", reviewPath(id), sender.token(), input(true));
        ok("POST", reviewPath(id), agent.token(), input(false));
        assertEquals(2, ok("GET", reviewPath(id), admin, null).get("reviews").size());
        assertEquals("COMPLETED", packages.findById(id).orElseThrow().getStatus().name());
    }
}
