package com.habeshlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.entity.enums.MarketplaceTypes.VerificationLevel;
import com.habeshlist.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:traveler-onboarding;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "app.demo-operational-data-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TravelerOnboardingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired TravelerProfileRepository profiles;
    @Autowired MarketTripRepository trips;

    private ObjectNode input() {
        return mapper.valueToTree(Map.of("firstName", " New ", "lastName", " Traveler ",
                "email", UUID.randomUUID() + "@example.com", "phone", "+1 (202) 555-0100",
                "password", "Traveler-onboarding-123", "termsAccepted", true,
                "tripDetails", Map.of("origin", " Washington DC ", "destination", " Addis Ababa ",
                        "departureDate", LocalDate.now().plusDays(1).toString(),
                        "expectedArrivalDate", LocalDate.now().plusDays(2).toString(),
                        "capacityKg", 5.25, "allowedItems", List.of("CLOTHING", "LAPTOP"),
                        "blockedItems", List.of("FOOD"))));
    }

    @Test
    void createsAccountAndTripSavesConsentAndLeavesVerificationUnchanged() throws Exception {
        ObjectNode input = input();
        Instant before = Instant.now();
        var response = mvc.perform(post("/api/auth/register/traveler").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andReturn().getResponse();
        var result = mapper.readTree(response.getContentAsString());
        var user = users.findByEmailIgnoreCase(input.path("email").asText()).orElseThrow();
        assertEquals(Role.TRAVELER, user.getRole());
        assertEquals("New", user.getFirstName());
        assertEquals("Traveler", user.getLastName());
        assertEquals("TRAVELER", result.path("auth").path("user").path("role").asText());
        var profile = profiles.findById(user.getId()).orElseThrow();
        assertEquals(VerificationLevel.UNVERIFIED, profile.getMarketplaceVerification());
        assertNull(profile.getVerificationStatus());
        assertNotNull(profile.getTermsAcceptedAt());
        assertFalse(profile.getTermsAcceptedAt().isBefore(before));
        assertEquals("traveler-community-v1", profile.getTermsVersion());
        var trip = trips.findById(UUID.fromString(result.path("tripId").asText())).orElseThrow();
        assertEquals(user.getId(), trip.getTraveler().getId());
        assertEquals("Washington DC", trip.getOrigin());
        assertEquals("Addis Ababa", trip.getDestination());
        assertEquals(5.25, trip.getCapacityKg().doubleValue());
        assertEquals(0, trip.getReservedKg().signum());
        String token = result.path("auth").path("token").asText();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TRAVELER"));
        mvc.perform(get("/api/marketplace/trips").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(trip.getId().toString()))
                .andExpect(jsonPath("$[0].traveler.verification").value("UNVERIFIED"))
                .andExpect(jsonPath("$[0].allowedItems.length()").value(2))
                .andExpect(jsonPath("$[0].blockedItems[0]").value("FOOD"));
        mvc.perform(get("/api/public/travel")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + trip.getId() + "')].origin").value("Washington DC"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", input.path("email").asText(),
                        "password", input.path("password").asText())))).andExpect(status().isOk());
        mvc.perform(put("/api/marketplace/travelers/" + user.getId() + "/verification")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\":\"ID_VERIFIED\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/marketplace/requests/" + UUID.randomUUID() + "/offers")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("tripId", trip.getId(), "price", 35))))
                .andExpect(status().isForbidden());
    }

    @Test
    void persistsCategoryPricesAndHandoffDetailsAfterOnboarding() throws Exception {
        ObjectNode input = input();
        ObjectNode details = (ObjectNode) input.get("tripDetails");
        details.put("handoffLocation", " Public airport meeting point ");
        details.put("notes", " Sealed items only ");
        details.set("itemPrices", mapper.valueToTree(List.of(
                Map.of("category", "CLOTHING", "price", 2.50, "unit", "PER_KG"),
                Map.of("category", "LAPTOP", "price", 25, "unit", "PER_ITEM"))));
        var response = mvc.perform(post("/api/auth/register/traveler").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andReturn().getResponse();
        var result = mapper.readTree(response.getContentAsString());
        var trip = trips.findById(UUID.fromString(result.path("tripId").asText())).orElseThrow();
        assertEquals("Public airport meeting point", trip.getHandoffLocation());
        assertEquals("Sealed items only", trip.getNotes());
        var responseTrips = mvc.perform(get("/api/marketplace/trips")
                .header("Authorization", "Bearer " + result.path("auth").path("token").asText()))
                .andExpect(status().isOk()).andReturn().getResponse();
        var row = mapper.readTree(responseTrips.getContentAsString()).get(0);
        assertEquals(2, row.get("itemPrices").size());
        assertEquals("CLOTHING", row.get("itemPrices").get(0).get("category").asText());
        assertEquals(2.5, row.get("itemPrices").get(0).get("price").asDouble());
        assertEquals("PER_KG", row.get("itemPrices").get(0).get("unit").asText());
        assertEquals("PER_ITEM", row.get("itemPrices").get(1).get("unit").asText());
    }

    @Test
    void invalidCategoryPricesRollBackAccountAndTrip() throws Exception {
        for (String problem : List.of("duplicate", "missing", "extra", "zero", "negative", "precision",
                "tooLarge", "invalidUnit", "nullItem", "longHandoff", "longNotes")) {
            ObjectNode input = input();
            ObjectNode details = (ObjectNode) input.get("tripDetails");
            details.set("itemPrices", mapper.valueToTree(List.of(
                    Map.of("category", "CLOTHING", "price", 2.5, "unit", "PER_KG"),
                    Map.of("category", "LAPTOP", "price", 25, "unit", "PER_ITEM"))));
            var prices = (com.fasterxml.jackson.databind.node.ArrayNode) details.get("itemPrices");
            switch (problem) {
                case "duplicate" -> ((ObjectNode) prices.get(1)).put("category", "CLOTHING");
                case "missing" -> prices.remove(1);
                case "extra" -> ((ObjectNode) prices.get(1)).put("category", "FOOD");
                case "zero" -> ((ObjectNode) prices.get(0)).put("price", 0);
                case "negative" -> ((ObjectNode) prices.get(0)).put("price", -1);
                case "precision" -> ((ObjectNode) prices.get(0)).put("price", 0.123);
                case "tooLarge" -> ((ObjectNode) prices.get(0)).put("price", 100000000);
                case "invalidUnit" -> ((ObjectNode) prices.get(0)).put("unit", "PER_BAG");
                case "nullItem" -> prices.addNull();
                case "longHandoff" -> details.put("handoffLocation", "H".repeat(501));
                case "longNotes" -> details.put("notes", "N".repeat(1001));
                default -> throw new IllegalStateException(problem);
            }
            long before = trips.count();
            mvc.perform(post("/api/auth/register/traveler").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
            assertFalse(users.existsByEmailIgnoreCase(input.path("email").asText()), problem);
            assertEquals(before, trips.count(), problem);
        }
    }

    @Test
    void acceptsTodaySameDayArrivalAndExactCapacityLimits() throws Exception {
        for (double capacity : List.of(0.1, 99999999.99)) {
            ObjectNode input = input();
            ObjectNode trip = (ObjectNode) input.get("tripDetails");
            trip.put("departureDate", LocalDate.now().toString());
            trip.put("expectedArrivalDate", LocalDate.now().toString());
            trip.put("capacityKg", capacity);
            var response = mvc.perform(post("/api/auth/register/traveler").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andReturn().getResponse();
            var result = mapper.readTree(response.getContentAsString());
            var saved = trips.findById(UUID.fromString(result.path("tripId").asText())).orElseThrow();
            assertEquals(capacity, saved.getCapacityKg().doubleValue());
            assertEquals(LocalDate.now(), saved.getDepartureDate());
            assertEquals(LocalDate.now(), saved.getExpectedArrivalDate());
        }
    }

    @Test
    void rejectsMalformedAccountTripAndMissingConsentWithoutPersistingAnything() throws Exception {
        for (String problem : List.of("termsFalse", "termsMissing", "phoneMissing", "phoneInvalid", "blankName",
                "shortPassword", "passwordBytes", "tripMissing", "pastDeparture", "arrivalBeforeDeparture",
                "arrivalMissing", "overlap", "emptyCategories", "negativeCapacity", "capacityPrecision", "capacityTooLarge", "blankOrigin")) {
            ObjectNode input = input();
            ObjectNode trip = (ObjectNode) input.get("tripDetails");
            switch (problem) {
                case "termsFalse" -> input.put("termsAccepted", false);
                case "termsMissing" -> input.remove("termsAccepted");
                case "phoneMissing" -> input.remove("phone");
                case "phoneInvalid" -> input.put("phone", "-------");
                case "blankName" -> input.put("firstName", " ");
                case "shortPassword" -> input.put("password", "Short");
                case "passwordBytes" -> input.put("password", "\u00e9".repeat(40));
                case "tripMissing" -> input.remove("tripDetails");
                case "pastDeparture" -> trip.put("departureDate", LocalDate.now().minusDays(1).toString());
                case "arrivalBeforeDeparture" -> trip.put("expectedArrivalDate", LocalDate.now().toString());
                case "arrivalMissing" -> trip.remove("expectedArrivalDate");
                case "overlap" -> trip.putArray("blockedItems").add("CLOTHING");
                case "emptyCategories" -> trip.putArray("allowedItems");
                case "negativeCapacity" -> trip.put("capacityKg", -1);
                case "capacityPrecision" -> trip.put("capacityKg", 0.123);
                case "capacityTooLarge" -> trip.put("capacityKg", 100000000);
                case "blankOrigin" -> trip.put("origin", " ");
                default -> throw new IllegalStateException(problem);
            }
            long userCount = users.count(), tripCount = trips.count(), profileCount = profiles.count();
            mvc.perform(post("/api/auth/register/traveler").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
            assertFalse(users.existsByEmailIgnoreCase(input.path("email").asText()), problem);
            assertEquals(userCount, users.count(), problem);
            assertEquals(tripCount, trips.count(), problem);
            assertEquals(profileCount, profiles.count(), problem);
        }
    }

    @Test
    void duplicateEmailDoesNotCreateAnExtraTripAndLegacyRegistrationStillWorks() throws Exception {
        ObjectNode input = input();
        mvc.perform(post("/api/auth/register/traveler").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isOk());
        long before = trips.count();
        mvc.perform(post("/api/auth/register/traveler").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
        assertEquals(before, trips.count());
        ObjectNode legacy = input();
        legacy.remove(List.of("tripDetails", "termsAccepted"));
        legacy.put("role", "TRAVELER");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(legacy))).andExpect(status().isOk());
        assertEquals(before, trips.count());
        var user = users.findByEmailIgnoreCase(legacy.path("email").asText()).orElseThrow();
        assertNull(profiles.findById(user.getId()).orElseThrow().getTermsAcceptedAt());
    }
}
