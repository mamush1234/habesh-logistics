package com.habeshlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.habeshlist.agent.*;
import com.habeshlist.entity.enums.CommissionStatus;
import com.habeshlist.repository.DeliveryRequestRepository;
import com.habeshlist.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentPortalServiceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository users;
    @Autowired DeliveryRequestRepository requests;
    @Autowired AgentAuthorizationRepository authorizations;
    @Autowired AgentAuditLogRepository audit;
    @Autowired AgentCommissionRepository commissions;
    @Autowired AgentCommissionService commissionService;
    private final String origin = "Origin-" + UUID.randomUUID();
    private final String destination = "Destination-" + UUID.randomUUID();

    @Test
    void commissionIsRateTimesDeliveryPriceRoundedToCents() {
        assertEquals(new BigDecimal("4.00"), AgentCommissionService.calculate(new BigDecimal("0.10"), new BigDecimal("40")));
        assertEquals(new BigDecimal("4.56"), AgentCommissionService.calculate(new BigDecimal("0.1000"), new BigDecimal("45.55")));
        assertEquals(new BigDecimal("0.00"), AgentCommissionService.calculate(BigDecimal.ZERO, new BigDecimal("99.99")));
        assertThrows(IllegalArgumentException.class,
                () -> AgentCommissionService.calculate(new BigDecimal("1.5"), BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> AgentCommissionService.calculate(new BigDecimal("0.1"), new BigDecimal("-1")));
    }

    @Test
    void onlyAgentsReachTheAgentPortal() throws Exception {
        call("GET", "/api/agent/dashboard", null, null).andExpect(status().isUnauthorized());
        call("GET", "/api/agent/dashboard", login("sender@example.com", "Sender@123"), null).andExpect(status().isForbidden());
        call("GET", "/api/agent/dashboard", admin(), null).andExpect(status().isForbidden());
        ok("GET", "/api/agent/dashboard", login("agent@example.com", "Agent@123"), null);
    }

    @Test
    void onBehalfActionsRequireActiveUnexpiredAuthorizationAndAreAudited() throws Exception {
        String agent = newAgent(null);
        JsonNode created = createCustomer(agent, "SENDER");
        String customerId = created.get("customer").get("id").asText();
        assertEquals("ACTIVE", created.get("authorization").get("status").asText());
        assertFalse(created.get("credentials").get("temporaryPassword").asText().isBlank());
        assertTrue(users.findById(UUID.fromString(customerId)).orElseThrow().isMustChangePassword());

        long before = audit.count();
        JsonNode pkg = created("POST", packages(customerId), agent, packageInput(false));
        assertEquals("REQUESTED", pkg.get("status").asText());
        assertEquals(before + 1, audit.count());
        var request = requests.findById(UUID.fromString(pkg.get("id").asText())).orElseThrow();
        assertEquals(customerId, request.getSender().getId().toString());
        assertNotNull(request.getCreatedByAgent());

        // Another agent cannot act for this customer at all.
        call("POST", packages(customerId), newAgent(null), packageInput(false)).andExpect(status().isForbidden());

        // Expired authorization is rejected without writing an audit row.
        var authorization = authorizations.findById(UUID.fromString(created.get("authorization").get("id").asText())).orElseThrow();
        authorization.setGrantedAt(Instant.now().minusSeconds(7200));
        authorization.setExpiresAt(Instant.now().minusSeconds(60));
        long beforeExpired = audit.count();
        call("POST", packages(customerId), agent, packageInput(false)).andExpect(status().isForbidden());
        call("POST", "/api/agent/customers/" + customerId + "/trips", agent, tripInput()).andExpect(status().isForbidden());
        assertEquals(beforeExpired, audit.count());

        // A fresh grant restores access; revoking it removes access again.
        JsonNode grant = created("POST", "/api/agent/authorizations", agent,
                Map.of("customerId", customerId, "method", "PHONE", "validDays", 30));
        created("POST", packages(customerId), agent, packageInput(false));
        JsonNode revoked = ok("DELETE", "/api/agent/authorizations/" + grant.get("id").asText(), agent, null);
        assertEquals("REVOKED", revoked.get("status").asText());
        call("POST", packages(customerId), agent, packageInput(false)).andExpect(status().isForbidden());
        // Sender customers cannot have trips posted for them.
        created("POST", "/api/agent/authorizations", agent, Map.of("customerId", customerId, "method", "IN_PERSON"));
        call("POST", "/api/agent/customers/" + customerId + "/trips", agent, tripInput()).andExpect(status().isForbidden());
        assertTrue(audit.findByAgentIdOrderByCreatedAtDesc(request.getCreatedByAgent().getId()).stream()
                .map(AgentAuditLog::getAction).toList()
                .containsAll(List.of("CUSTOMER_CREATED", "AUTHORIZATION_GRANTED", "PACKAGE_CREATED", "AUTHORIZATION_REVOKED")));
    }

    @Test
    void customerCreationRequiresConfirmedAuthorizationAndScopesListsToTheAgent() throws Exception {
        String agent = newAgent(null);
        var input = customerInput("TRAVELER");
        input.put("authorization", Map.of("confirmed", false, "method", "IN_PERSON"));
        call("POST", "/api/agent/customers", agent, input).andExpect(status().isBadRequest());
        JsonNode traveler = createCustomer(agent, "TRAVELER");
        createCustomer(agent, "SENDER");
        String travelerId = traveler.get("customer").get("id").asText();
        created("POST", "/api/agent/customers/" + travelerId + "/trips", agent, tripInput());
        JsonNode travelers = ok("GET", "/api/agent/customers?type=TRAVELER", agent, null);
        assertEquals(1, travelers.get("totalItems").asInt());
        assertEquals(1, travelers.get("items").get(0).get("tripCount").asInt());
        assertEquals(2, ok("GET", "/api/agent/customers", agent, null).get("totalItems").asInt());
        assertEquals(0, ok("GET", "/api/agent/customers", newAgent(null), null).get("totalItems").asInt());
        call("GET", "/api/agent/customers/" + travelerId, newAgent(null), null).andExpect(status().isForbidden());
        call("POST", packages(travelerId), agent, packageInput(false)).andExpect(status().isForbidden());
        // Existing accounts are never silently linked to an agent.
        var duplicate = customerInput("SENDER");
        duplicate.put("email", traveler.get("customer").get("email").asText());
        call("POST", "/api/agent/customers", agent, duplicate).andExpect(status().isBadRequest());
    }

    @Test
    void draftsStayPrivateUntilSubmitted() throws Exception {
        String agent = newAgent(null);
        String customerId = createCustomer(agent, "SENDER").get("customer").get("id").asText();
        JsonNode draft = created("POST", packages(customerId), agent, packageInput(true));
        assertEquals("DRAFT", draft.get("status").asText());
        String requestPath = "/api/marketplace/requests/" + draft.get("id").asText();
        call("GET", requestPath, login("agent@example.com", "Agent@123"), null).andExpect(status().isForbidden());
        call("PUT", requestPath + "/approval", admin(), Map.of("approved", true)).andExpect(status().isBadRequest());
        JsonNode submitted = ok("POST", packages(customerId) + "/" + draft.get("id").asText() + "/submit", agent, null);
        assertEquals("REQUESTED", submitted.get("status").asText());
        call("POST", packages(customerId) + "/" + draft.get("id").asText() + "/submit", agent, null)
                .andExpect(status().isBadRequest());
        assertEquals(1, ok("GET", "/api/agent/packages?status=REQUESTED", agent, null).get("totalItems").asInt());
    }

    @Test
    void authorizedDraftIncludesStructuredItemsAndReceiverAndPreservesThemWhenSubmitted() throws Exception {
        String agent = newAgent(null);
        String customerId = createCustomer(agent, "SENDER").get("customer").get("id").asText();
        Map<String, Object> details = new HashMap<>(Map.of("pickupCity", origin, "destinationCity", destination,
                "availableFrom", date(1), "deadline", date(5), "itemType", "OTHER",
                "description", "Customer items", "weightKg", 0.1, "offeredPrice", 50));
        details.put("items", List.of(Map.of("category", "CLOTHING", "weightKg", 2, "description", "Clothing"),
                Map.of("category", "DOCUMENTS", "weightKg", 0.5, "description", "Documents")));
        details.put("receiver", Map.of("name", "Receiver", "phone", "+251911234567", "address", "Addis Ababa"));
        JsonNode draft = created("POST", packages(customerId), agent, Map.of("details", details, "draft", true));
        assertEquals(2.5, draft.get("weightKg").asDouble());
        assertEquals(2, draft.get("items").size());
        assertEquals("Receiver", draft.get("receiver").get("name").asText());
        JsonNode submitted = ok("POST", packages(customerId) + "/" + draft.get("id").asText() + "/submit", agent, null);
        assertEquals(draft.get("items"), submitted.get("items"));
        assertEquals(draft.get("receiver"), submitted.get("receiver"));
        assertEquals("REQUESTED", submitted.get("status").asText());
        assertEquals("Receiver", ok("GET", "/api/agent/packages", agent, null).get("items").get(0).get("receiver").get("name").asText());
        assertEquals(0, ok("GET", "/api/agent/packages", newAgent(null), null).get("totalItems").asInt());
    }

    @Test
    void completedAgentDeliveryCreatesOnePendingCommission() throws Exception {
        String agent = newAgent(new BigDecimal("0.15"));
        JsonNode customer = createCustomer(agent, "SENDER");
        String customerId = customer.get("customer").get("id").asText();
        JsonNode pkg = created("POST", packages(customerId), agent, packageInput(false));
        var customerUser = users.findById(UUID.fromString(customerId)).orElseThrow();
        customerUser.setMustChangePassword(false);
        String sender = login(customer.get("credentials").get("email").asText(),
                customer.get("credentials").get("temporaryPassword").asText());

        String carrier = newAgent(null);
        JsonNode trip = ok("POST", "/api/marketplace/trips", carrier, tripInput());
        String path = "/api/marketplace/requests/" + pkg.get("id").asText();
        JsonNode offered = ok("POST", path + "/offers", carrier, Map.of("tripId", trip.get("id").asText(), "price", 40));
        ok("POST", path + "/offers/" + offered.get("offers").get(0).get("id").asText() + "/accept", sender, null);
        ok("POST", path + "/demo-escrow", sender, null);
        for (String s : List.of("PICKED_UP", "IN_TRANSIT", "ARRIVED")) ok("PUT", path + "/status", carrier, Map.of("status", s));
        assertEquals(0, ok("GET", "/api/agent/commissions", agent, null).get("totalItems").asInt());
        String code = ok("POST", path + "/delivery-confirmation/code", sender, null).get("code").asText();
        ok("POST", path + "/delivery-confirmation/verify", carrier, Map.of("code", code));

        var request = requests.findById(UUID.fromString(pkg.get("id").asText())).orElseThrow();
        AgentCommission commission = commissions.findByRequestId(request.getId()).orElseThrow();
        assertEquals(CommissionStatus.PENDING, commission.getStatus());
        assertEquals(0, new BigDecimal("0.15").compareTo(commission.getRate()));
        assertEquals(new BigDecimal("40.00"), commission.getBaseAmount());
        assertEquals(new BigDecimal("6.00"), commission.getAmount());
        assertTrue(commissionService.recordCompletedDelivery(request).isEmpty());
        assertEquals(1, commissions.findByAgentIdOrderByCreatedAtDesc(request.getCreatedByAgent().getId()).size());

        JsonNode page = ok("GET", "/api/agent/commissions?status=PENDING&from=" + LocalDate.now().minusDays(1)
                + "&to=" + LocalDate.now().plusDays(1), agent, null);
        assertEquals(1, page.get("totalItems").asInt());
        assertEquals(0, new BigDecimal("6.00").compareTo(page.get("pending").decimalValue()));
        assertEquals(0, new BigDecimal("6.00").compareTo(page.get("total").decimalValue()));
        assertEquals(0, ok("GET", "/api/agent/commissions", carrier, null).get("totalItems").asInt());

        ok("POST", "/api/admin/agents/commissions/" + commission.getId() + "/pay", admin(), null);
        JsonNode paid = ok("GET", "/api/agent/commissions", agent, null);
        assertEquals(0, new BigDecimal("6.00").compareTo(paid.get("paid").decimalValue()));
        assertEquals("PAID", paid.get("items").get(0).get("status").asText());
    }

    @Test
    void onBehalfTripPricesAreScopedAndAuditedAndRequireAuthorization() throws Exception {
        String agent = newAgent(null);
        JsonNode customer = createCustomer(agent, "TRAVELER");
        String customerId = customer.get("customer").get("id").asText();
        String url = "/api/agent/customers/" + customerId + "/trips";
        var input = tripInput();
        input.put("handoffLocation", " Airport public meeting point ");
        input.put("notes", " Fragile items need packaging ");
        input.put("itemPrices", List.of(
                Map.of("category", "CLOTHING", "price", 5, "unit", "PER_KG"),
                Map.of("category", "DOCUMENTS", "price", 10, "unit", "PER_ITEM")));
        long before = audit.count();
        JsonNode saved = created("POST", url, agent, input);
        assertEquals(before + 1, audit.count());
        assertEquals(2, saved.get("itemPrices").size());
        assertEquals("Airport public meeting point", saved.get("handoffLocation").asText());
        assertEquals("Fragile items need packaging", saved.get("notes").asText());
        JsonNode listed = ok("GET", "/api/agent/trips", agent, null).get("items").get(0);
        assertEquals(saved.get("itemPrices"), listed.get("itemPrices"));
        String other = newAgent(null);
        assertEquals(0, ok("GET", "/api/agent/trips", other, null).get("totalItems").asInt());
        call("POST", url, other, input).andExpect(status().isForbidden());
        input.put("itemPrices", List.of(Map.of("category", "FOOD", "price", 5, "unit", "PER_KG")));
        before = audit.count();
        call("POST", url, agent, input).andExpect(status().isBadRequest());
        assertEquals(before, audit.count());
        ok("DELETE", "/api/agent/authorizations/" + customer.get("authorization").get("id").asText(), agent, null);
        call("POST", url, agent, tripInput()).andExpect(status().isForbidden());
    }

    @Test
    void adminCanPromoteExistingUserToAgent() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        JsonNode user = ok("POST", "/api/admin/users", admin(), Map.of("email", email,
                "password", "Promote-me-password-2026", "firstName", "Promo", "lastName", "Ted", "role", "SENDER"));
        String token = login(email, "Promote-me-password-2026");
        call("GET", "/api/agent/dashboard", token, null).andExpect(status().isForbidden());
        JsonNode promoted = ok("POST", "/api/admin/agents/" + user.get("id").asText() + "/promote", admin(),
                Map.of("commissionRate", 0.12));
        assertEquals(0, new BigDecimal("0.12").compareTo(promoted.get("commissionRate").decimalValue()));
        call("GET", "/api/agent/dashboard", token, null).andExpect(status().isUnauthorized());
        ok("GET", "/api/agent/dashboard", login(email, "Promote-me-password-2026"), null);
        String adminId = users.findByEmailIgnoreCase("admin@habeshlist.com").orElseThrow().getId().toString();
        call("POST", "/api/admin/agents/" + adminId + "/promote", admin(), null).andExpect(status().isBadRequest());
        call("POST", "/api/admin/agents/" + user.get("id").asText() + "/promote", token, null).andExpect(status().isUnauthorized());
    }

    private String packages(String customerId) { return "/api/agent/customers/" + customerId + "/packages"; }
    private String date(int days) { return LocalDate.now().plusDays(days).toString(); }
    private Map<String, Object> packageInput(boolean draft) {
        Map<String, Object> input = new HashMap<>();
        input.put("details", Map.of("pickupCity", origin, "destinationCity", destination, "availableFrom", date(1),
                "deadline", date(5), "itemType", "CLOTHING", "description", "Sealed clothing parcel",
                "weightKg", 4, "offeredPrice", 50));
        input.put("draft", draft);
        return input;
    }
    private Map<String, Object> tripInput() {
        return new HashMap<>(Map.of("origin", origin, "destination", destination, "departureDate", date(3),
                "expectedArrivalDate", date(4), "capacityKg", 5, "allowedItems", List.of("CLOTHING", "DOCUMENTS"),
                "blockedItems", List.of("FOOD", "MEDICINE")));
    }
    private Map<String, Object> customerInput(String type) {
        return new HashMap<>(Map.of("customerType", type, "firstName", "Cus", "lastName", "Tomer",
                "email", UUID.randomUUID() + "@example.com", "phone", "+1 202 555 0100",
                "authorization", Map.of("confirmed", true, "method", "IN_PERSON", "consentNote", "Signed form")));
    }
    private JsonNode createCustomer(String agent, String type) throws Exception {
        return created("POST", "/api/agent/customers", agent, customerInput(type));
    }
    private String newAgent(BigDecimal rate) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        JsonNode user = ok("POST", "/api/admin/users", admin(), Map.of("email", email,
                "password", "New-agent-password-2026", "firstName", "Portal", "lastName", "Agent", "role", "AGENT"));
        if (rate != null) ok("POST", "/api/admin/agents/" + user.get("id").asText() + "/promote", admin(),
                Map.of("commissionRate", rate));
        return login(email, "New-agent-password-2026");
    }
    private String admin() throws Exception { return login("admin@habeshlist.com", "Admin@123"); }
    private String login(String email, String password) throws Exception {
        return ok("POST", "/api/auth/login", null, Map.of("email", email, "password", password)).get("token").asText();
    }
    private ResultActions call(String method, String path, String token, Object body) throws Exception {
        var request = switch (method) {
            case "GET" -> get(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            default -> post(path);
        };
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return mvc.perform(request);
    }
    private JsonNode ok(String method, String path, String token, Object body) throws Exception {
        return om.readTree(call(method, path, token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode created(String method, String path, String token, Object body) throws Exception {
        return om.readTree(call(method, path, token, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
}
