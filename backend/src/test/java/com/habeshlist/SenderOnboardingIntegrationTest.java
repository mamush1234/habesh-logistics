package com.habeshlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.habeshlist.entity.enums.MarketplaceTypes.*;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sender-onboarding;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "app.demo-operational-data-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SenderOnboardingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired DeliveryRequestRepository requests;
    @Autowired DeliveryEventRepository events;

    private ObjectNode multiItemInput() {
        ObjectNode input = mapper.valueToTree(input("OTHER"));
        ObjectNode details = (ObjectNode) input.get("packageDetails");
        details.set("items", mapper.valueToTree(List.of(
                Map.of("category", "CLOTHING", "weightKg", 0.1, "description", " Shirt "),
                Map.of("category", "LAPTOP", "weightKg", 0.2, "description", "Laptop"))));
        details.set("receiver", mapper.valueToTree(Map.of("name", " Receiver ", "phone", "+251 911 234567",
                "address", " Bole, Addis Ababa ", "notes", " Call before arrival ")));
        return input;
    }

    @Test
    void persistsOrderedItemsAndReceiverAndCalculatesWeightInsteadOfTrustingScalar() throws Exception {
        ObjectNode input = multiItemInput();
        var response = mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andReturn().getResponse();
        var result = mapper.readTree(response.getContentAsString());
        mvc.perform(get("/api/marketplace/requests/" + result.path("requestId").asText())
                .header("Authorization", "Bearer " + result.path("auth").path("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(0.3))
                .andExpect(jsonPath("$.itemType").value("CLOTHING"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].description").value("Shirt"))
                .andExpect(jsonPath("$.items[1].category").value("LAPTOP"))
                .andExpect(jsonPath("$.receiver.name").value("Receiver"))
                .andExpect(jsonPath("$.receiver.address").value("Bole, Addis Ababa"))
                .andExpect(jsonPath("$.receiver.notes").value("Call before arrival"));
    }

    @Test
    void acceptsExactlyFiftyItemsAndTheMaximumTotalWeight() throws Exception {
        ObjectNode input = multiItemInput();
        ObjectNode details = (ObjectNode) input.get("packageDetails");
        var items = new ArrayList<Map<String, Object>>();
        items.add(Map.of("category", "CLOTHING", "weightKg", new BigDecimal("99999995.09"), "description", "First item"));
        for (int i = 1; i < 50; i++)
            items.add(Map.of("category", "OTHER", "weightKg", new BigDecimal("0.10"), "description", "Item " + i));
        details.set("items", mapper.valueToTree(items));
        var response = mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andReturn().getResponse();
        var result = mapper.readTree(response.getContentAsString());
        mvc.perform(get("/api/marketplace/requests/" + result.path("requestId").asText())
                .header("Authorization", "Bearer " + result.path("auth").path("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(99999999.99))
                .andExpect(jsonPath("$.items.length()").value(50))
                .andExpect(jsonPath("$.items[49].description").value("Item 49"));
    }

    @Test
    void acceptsReceiverAddressAndNotesAtExistingSchemaLimits() throws Exception {
        ObjectNode input = multiItemInput();
        ObjectNode receiver = (ObjectNode) input.path("packageDetails").path("receiver");
        receiver.put("address", "A".repeat(500));
        receiver.put("notes", "N".repeat(1000));
        var response = mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andReturn().getResponse();
        var result = mapper.readTree(response.getContentAsString());
        mvc.perform(get("/api/marketplace/requests/" + result.path("requestId").asText())
                .header("Authorization", "Bearer " + result.path("auth").path("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiver.address").value("A".repeat(500)))
                .andExpect(jsonPath("$.receiver.notes").value("N".repeat(1000)));
    }

    @Test
    void invalidStructuredContentsNeverCreateAnAccountOrPackage() throws Exception {
        for (String problem : List.of("empty", "tooMany", "nullItem", "negative", "precision", "total",
                "missingReceiver", "missingItems", "receiverPhone", "receiverAddress", "longAddress", "longNotes", "blankDescription")) {
            ObjectNode input = multiItemInput();
            ObjectNode details = (ObjectNode) input.get("packageDetails");
            switch (problem) {
                case "empty" -> details.putArray("items");
                case "tooMany" -> details.set("items", mapper.valueToTree(Collections.nCopies(51,
                        Map.of("category", "OTHER", "weightKg", 1, "description", "Item"))));
                case "nullItem" -> details.putArray("items").addNull();
                case "negative", "precision", "total", "blankDescription" -> details.set("items", mapper.valueToTree(List.of(
                        Map.of("category", "OTHER", "weightKg", switch (problem) {
                            case "negative" -> -1; case "precision" -> 0.123; case "total" -> 99999999.99; default -> 1;
                        }, "description", problem.equals("blankDescription") ? " " : "Item"),
                        Map.of("category", "CLOTHING", "weightKg", 1, "description", "Shirt"))));
                case "missingReceiver" -> details.remove("receiver");
                case "missingItems" -> details.remove("items");
                case "receiverPhone" -> ((ObjectNode) details.get("receiver")).put("phone", "-------");
                case "receiverAddress" -> ((ObjectNode) details.get("receiver")).put("address", " ");
                case "longAddress" -> ((ObjectNode) details.get("receiver")).put("address", "A".repeat(501));
                case "longNotes" -> ((ObjectNode) details.get("receiver")).put("notes", "N".repeat(1001));
                default -> throw new IllegalStateException(problem);
            }
            long before = requests.count();
            mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
            assertFalse(users.existsByEmailIgnoreCase(input.path("email").asText()), problem);
            assertEquals(before, requests.count(), problem);
        }
    }

    private Map<String, Object> input(String category) {
        var details = new HashMap<String, Object>(Map.of("pickupCity", "Washington DC", "destinationCity", "Addis Ababa",
                "availableFrom", LocalDate.now().toString(), "deadline", LocalDate.now().plusDays(7).toString(),
                "itemType", category, "description", "Sealed package", "weightKg", 2.5, "offeredPrice", 35));
        return new HashMap<>(Map.of("firstName", "New", "lastName", "Sender", "email", UUID.randomUUID() + "@example.com",
                "phone", "+1 (202) 555-0100", "password", "Sender-onboarding-123", "packageDetails", details));
    }

    @Test
    void createsSenderPackageTimelineAndUsableLoginForEveryDesignCategory() throws Exception {
        for (String category : List.of("CLOTHING", "SHOES", "LAPTOP", "ELECTRONICS", "COSMETICS", "OTHER")) {
            var input = input(category);
            var response = mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andReturn().getResponse();
            var result = mapper.readTree(response.getContentAsString());
            assertEquals("SENDER", result.path("auth").path("user").path("role").asText());
            var sender = users.findByEmailIgnoreCase((String) input.get("email")).orElseThrow();
            assertEquals(Role.SENDER, sender.getRole());
            var request = requests.findById(UUID.fromString(result.path("requestId").asText())).orElseThrow();
            assertEquals(sender.getId(), request.getSender().getId());
            assertEquals(ItemType.valueOf(category), request.getItemType());
            assertEquals(DeliveryStatus.REQUESTED, request.getStatus());
            assertFalse(request.isAdminApproved());
            assertEquals(sender.getId(), events.findByRequestIdOrderBySequenceNumberAsc(request.getId()).getFirst().getActorId());
            mvc.perform(get("/api/marketplace/requests/" + request.getId())
                    .header("Authorization", "Bearer " + result.path("auth").path("token").asText()))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("email", input.get("email"), "password", input.get("password")))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void invalidDateWindowRollsBackAccountAndPackage() throws Exception {
        var input = input("CLOTHING");
        input.put("packageDetails", Map.of("pickupCity", "DC", "destinationCity", "Addis",
                "availableFrom", LocalDate.now().plusDays(3).toString(), "deadline", LocalDate.now().toString(),
                "itemType", "CLOTHING", "description", "Package", "weightKg", 1, "offeredPrice", 20));
        long before = requests.count();
        mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
        assertFalse(users.existsByEmailIgnoreCase((String) input.get("email")));
        assertEquals(before, requests.count());
    }

    @Test
    void requiresPhoneAndValidPackageBeforeCreatingAccount() throws Exception {
        var input = input("LAPTOP");
        input.put("phone", "");
        mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
        assertFalse(users.existsByEmailIgnoreCase((String) input.get("email")));
        input.put("phone", "-------");
        mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
        assertFalse(users.existsByEmailIgnoreCase((String) input.get("email")));
        input.put("phone", "+12025550100");
        input.put("packageDetails", null);
        mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
        assertFalse(users.existsByEmailIgnoreCase((String) input.get("email")));
    }

    @Test
    void duplicateEmailDoesNotCreateAnotherPackageAndLegacyRegistrationStillWorks() throws Exception {
        var input = input("SHOES");
        mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isOk());
        long before = requests.count();
        mvc.perform(post("/api/auth/register/sender").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input))).andExpect(status().isBadRequest());
        assertEquals(before, requests.count());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("firstName", "New", "lastName", "Traveler",
                        "email", UUID.randomUUID() + "@example.com", "password", "Traveler-registration-123", "role", "TRAVELER"))))
                .andExpect(status().isOk());
    }
}
