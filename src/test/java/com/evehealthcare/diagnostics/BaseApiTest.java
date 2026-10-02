package com.evehealthcare.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class BaseApiTest {
    protected static final String ADMIN_EMAIL = "admin@eve.test";
    protected static final String ADMIN_PASSWORD = "Admin@12345";
    protected static final String WEBHOOK_SECRET = "test-webhook-secret";
    protected static final String USER_PASSWORD = "Passw0rd!x";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;

    protected record Catalog(long centreId, long testId) {}

    protected ResultActions post(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.post(url), token, body);
    }

    protected ResultActions put(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.put(url), token, body);
    }

    protected ResultActions get(String url, String token) throws Exception {
        return send(MockMvcRequestBuilders.get(url), token, null);
    }

    private ResultActions send(MockHttpServletRequestBuilder b, String token, Object body) throws Exception {
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : om.writeValueAsString(body));
        }
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(b);
    }

    protected JsonNode json(ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString());
    }

    protected String login(String email, String password) throws Exception {
        ResultActions r = post("/auth/login", null, Map.of("email", email, "password", password));
        r.andExpect(status().isOk());
        return json(r).get("accessToken").asText();
    }

    protected String newUserToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        post("/auth/signup", null, Map.of("email", email, "password", USER_PASSWORD, "fullName", "Test User"))
                .andExpect(status().isCreated());
        return login(email, USER_PASSWORD);
    }

    protected String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected Catalog createCatalog(String price) throws Exception {
        String admin = adminToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long testId = json(post("/tests", admin, Map.of("name", "Test-" + suffix, "description", "d"))
                .andExpect(status().isCreated())).get("id").asLong();
        long centreId = json(post("/centres", admin, Map.of("name", "Centre-" + suffix, "location", "Hyderabad"))
                .andExpect(status().isCreated())).get("id").asLong();
        put("/centres/" + centreId + "/tests/" + testId, admin, Map.of("price", new java.math.BigDecimal(price)))
                .andExpect(status().isOk());
        return new Catalog(centreId, testId);
    }

    protected String futureTime() {
        return Instant.now().plus(2, ChronoUnit.DAYS).toString();
    }

    protected long createBooking(String token, Catalog c) throws Exception {
        ResultActions r = post("/bookings", token,
                Map.of("centreId", c.centreId(), "testId", c.testId(), "appointmentTime", futureTime()));
        r.andExpect(status().isCreated());
        return json(r).get("id").asLong();
    }

    protected JsonNode pay(String token, long bookingId, String outcome) throws Exception {
        return json(post("/payments/", token, Map.of("bookingId", bookingId, "simulatedOutcome", outcome))
                .andExpect(status().isCreated()));
    }

    protected String bookingStatus(String token, long bookingId) throws Exception {
        return json(get("/bookings/" + bookingId, token).andExpect(status().isOk())).get("status").asText();
    }

    protected ResultActions webhook(String eventId, String paymentRef, String status) throws Exception {
        String body = om.writeValueAsString(Map.of("eventId", eventId, "paymentReference", paymentRef, "status", status));
        return rawWebhook(body, sign(body));
    }

    protected ResultActions rawWebhook(String body, String signature) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post("/payments/webhook/")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            b.header("X-Webhook-Signature", signature);
        }
        return mvc.perform(b);
    }

    protected String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    protected String uniqueEventId() {
        return "evt_" + UUID.randomUUID();
    }
}
