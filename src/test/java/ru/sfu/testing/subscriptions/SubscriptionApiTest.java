package ru.sfu.testing.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:subscription_api_test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SubscriptionApiTest {
    private static final String FIRST = "first@example.test";
    private static final String SECOND = "second@example.test";
    private static final String PASSWORD = "secure-demo-password";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void coversAccountIsolationCrudPaymentHistoryAndAnalytics() throws Exception {
        mvc.perform(get("/api/subscriptions")).andExpect(status().isUnauthorized());
        register(FIRST).andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist());
        register("FIRST@example.test").andExpect(status().isConflict());
        register(SECOND).andExpect(status().isCreated());
        String owner = login(FIRST);
        String outsider = login(SECOND);

        String input = subscription("Movie", "MEDIA", "19.90", "ACTIVE");
        mvc.perform(post("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscription("Invalid", "MEDIA", "0.00", "ACTIVE")))
                .andExpect(status().isBadRequest());
        JsonNode item = body(mvc.perform(post("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, owner)
                .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isCreated()));
        String id = item.get("id").asText();
        mvc.perform(get("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, outsider))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/subscriptions/{id}", id).header(HttpHeaders.AUTHORIZATION, outsider))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/subscriptions/{id}", id).header(HttpHeaders.AUTHORIZATION, outsider)
                .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/subscriptions/{id}", id).header(HttpHeaders.AUTHORIZATION, outsider))
                .andExpect(status().isNotFound());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mvc.perform(post("/api/payments").header(HttpHeaders.AUTHORIZATION, outsider)
                .contentType(MediaType.APPLICATION_JSON).content(payment(id, today)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/payments").header(HttpHeaders.AUTHORIZATION, owner)
                .contentType(MediaType.APPLICATION_JSON).content(payment(id, today.plusDays(1))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments").header(HttpHeaders.AUTHORIZATION, owner)
                .contentType(MediaType.APPLICATION_JSON).content(payment(id, today)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.category").value("MEDIA"));

        mvc.perform(put("/api/subscriptions/{id}", id).header(HttpHeaders.AUTHORIZATION, owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscription("Movie plus", "OTHER", "29.50", "PAUSED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(get("/api/subscriptions/{id}", id).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.category").value("OTHER"));
        mvc.perform(delete("/api/subscriptions/{id}", id).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/subscriptions/{id}", id).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/payments").header(HttpHeaders.AUTHORIZATION, owner)
                .param("from", today.toString()).param("to", today.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].subscriptionName").value("Movie"));
        mvc.perform(get("/api/analytics/expenses").header(HttpHeaders.AUTHORIZATION, owner)
                .param("from", today.toString()).param("to", today.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(19.9))
                .andExpect(jsonPath("$.byCategory.MEDIA").value(19.9));
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/subscriptions").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))));
    }

    private String login(String email) throws Exception {
        JsonNode response = body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        return "Bearer " + response.get("accessToken").asText();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private String subscription(String name, String category, String amount, String status) throws Exception {
        return json.writeValueAsString(Map.of("name", name, "category", category,
                "amount", new java.math.BigDecimal(amount), "billingPeriod", "MONTHLY",
                "nextChargeDate", "2026-12-01", "status", status));
    }

    private String payment(String id, LocalDate date) throws Exception {
        return json.writeValueAsString(Map.of("subscriptionId", id, "amount", 19.9, "paidOn", date.toString()));
    }
}
