package com.evehealthcare.diagnostics;

import com.evehealthcare.diagnostics.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingPaymentApiTest extends BaseApiTest {
    @Autowired PaymentRepository paymentRepository;

    @Test
    void happyPath_bookThenPay() throws Exception {
        Catalog c = createCatalog("1500.00");
        String user = newUserToken();

        long bookingId = json(post("/bookings/", user,
                Map.of("centreId", c.centreId(), "testId", c.testId(), "appointmentTime", futureTime()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(1500.00))).get("id").asLong();

        JsonNode payment = pay(user, bookingId, "SUCCESS");
        assertEquals("SUCCESS", payment.get("status").asText());
        assertEquals("CONFIRMED", payment.get("bookingStatus").asText());
        assertEquals("CONFIRMED", bookingStatus(user, bookingId));

        // paying a confirmed booking again must not create a second payment
        long before = paymentRepository.count();
        post("/payments/", user, Map.of("bookingId", bookingId, "simulatedOutcome", "SUCCESS"))
                .andExpect(status().isConflict());
        assertEquals(before, paymentRepository.count());
    }

    @Test
    void priceChangeAfterBookingDoesNotAffectAmount() throws Exception {
        Catalog c = createCatalog("200.00");
        String user = newUserToken();
        long bookingId = createBooking(user, c);
        put("/centres/" + c.centreId() + "/tests/" + c.testId(), adminToken(), Map.of("price", 999)).andExpect(status().isOk());
        get("/bookings/" + bookingId, user).andExpect(jsonPath("$.amount").value(200.00));
    }

    @Test
    void failedPaymentThenRetry() throws Exception {
        Catalog c = createCatalog("300.00");
        String user = newUserToken();
        long bookingId = createBooking(user, c);

        JsonNode failed = pay(user, bookingId, "FAILED");
        assertEquals("FAILED", failed.get("status").asText());
        assertEquals("FAILED", failed.get("bookingStatus").asText());
        assertEquals("FAILED", bookingStatus(user, bookingId));

        JsonNode ok = pay(user, bookingId, "SUCCESS");
        assertEquals("CONFIRMED", ok.get("bookingStatus").asText());
        assertEquals("CONFIRMED", bookingStatus(user, bookingId));
    }

    @Test
    void bookingValidation() throws Exception {
        Catalog c = createCatalog("100.00");
        String user = newUserToken();
        String past = Instant.now().minus(1, ChronoUnit.DAYS).toString();

        post("/bookings", user, Map.of("centreId", c.centreId(), "testId", c.testId(), "appointmentTime", past))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("appointmentTime"));
        post("/bookings", user, Map.of("centreId", c.centreId())).andExpect(status().isBadRequest());
        post("/bookings", user, Map.of("centreId", c.centreId(), "testId", c.testId(), "appointmentTime", "tomorrow-ish"))
                .andExpect(status().isBadRequest());
        post("/bookings", user, Map.of("centreId", 999999, "testId", c.testId(), "appointmentTime", futureTime()))
                .andExpect(status().isNotFound());
        post("/bookings", user, Map.of("centreId", c.centreId(), "testId", 999999, "appointmentTime", futureTime()))
                .andExpect(status().isNotFound());

        Catalog other = createCatalog("50.00");
        post("/bookings", user, Map.of("centreId", c.centreId(), "testId", other.testId(), "appointmentTime", futureTime()))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateActiveBookingIsRejected() throws Exception {
        Catalog c = createCatalog("100.00");
        String user = newUserToken();
        Map<String, Object> body = Map.of("centreId", c.centreId(), "testId", c.testId(),
                "appointmentTime", "2099-01-01T10:00:00Z");
        post("/bookings", user, body).andExpect(status().isCreated());
        post("/bookings", user, body).andExpect(status().isConflict());
    }

    @Test
    void usersCannotTouchEachOthersBookings() throws Exception {
        Catalog c = createCatalog("100.00");
        String owner = newUserToken();
        String intruder = newUserToken();
        long bookingId = createBooking(owner, c);

        get("/bookings/" + bookingId, intruder).andExpect(status().isNotFound());
        post("/payments/", intruder, Map.of("bookingId", bookingId, "simulatedOutcome", "SUCCESS"))
                .andExpect(status().isNotFound());
        post("/bookings/" + bookingId + "/cancel", intruder, null).andExpect(status().isNotFound());
        get("/bookings", intruder).andExpect(jsonPath("$.content", hasSize(0)));

        assertEquals("PENDING", bookingStatus(owner, bookingId));
    }

    @Test
    void invalidBookingIdsAndPaymentRequests() throws Exception {
        String user = newUserToken();
        post("/payments/", user, Map.of("bookingId", 999999)).andExpect(status().isNotFound());
        post("/payments/", user, Map.of()).andExpect(status().isBadRequest());
        post("/payments/", user, Map.of("bookingId", "abc")).andExpect(status().isBadRequest());
        post("/payments/", user, Map.of("bookingId", 1, "simulatedOutcome", "MAYBE")).andExpect(status().isBadRequest());
        get("/bookings/999999", user).andExpect(status().isNotFound());
        get("/bookings/abc", user).andExpect(status().isBadRequest());
        post("/payments/", null, Map.of("bookingId", 1)).andExpect(status().isUnauthorized());
    }

    @Test
    void cancelFlow() throws Exception {
        Catalog c = createCatalog("100.00");
        String user = newUserToken();
        long bookingId = createBooking(user, c);

        post("/bookings/" + bookingId + "/cancel", user, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        post("/bookings/" + bookingId + "/cancel", user, null).andExpect(status().isConflict());
        post("/payments/", user, Map.of("bookingId", bookingId, "simulatedOutcome", "SUCCESS"))
                .andExpect(status().isConflict());
    }

    @Test
    void idempotencyKeyReplayReturnsOriginalPayment() throws Exception {
        Catalog c = createCatalog("100.00");
        String user = newUserToken();
        long bookingId = createBooking(user, c);
        Map<String, Object> body = Map.of("bookingId", bookingId, "simulatedOutcome", "SUCCESS");

        long first = json(send(user, body, "key-" + bookingId).andExpect(status().isCreated())).get("id").asLong();
        long before = paymentRepository.count();
        long second = json(send(user, body, "key-" + bookingId).andExpect(status().isOk())).get("id").asLong();

        assertEquals(first, second);
        assertEquals(before, paymentRepository.count());

        long other = createBooking(user, c);
        send(user, Map.of("bookingId", other, "simulatedOutcome", "SUCCESS"), "key-" + bookingId)
                .andExpect(status().isConflict());
    }

    @Test
    void listFilterAndPayment() throws Exception {
        Catalog c = createCatalog("100.00");
        String user = newUserToken();
        long paid = createBooking(user, c);
        createBooking(user, c);
        long paymentId = pay(user, paid, "SUCCESS").get("id").asLong();

        get("/bookings", user).andExpect(jsonPath("$.totalElements").value(2));
        get("/bookings?status=CONFIRMED", user).andExpect(jsonPath("$.totalElements").value(1));
        get("/bookings?status=NOPE", user).andExpect(status().isBadRequest());

        get("/payments/" + paymentId, user).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        get("/payments/" + paymentId, newUserToken()).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions send(String token, Object body, String key) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/payments/")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .content(om.writeValueAsString(body)));
    }
}
