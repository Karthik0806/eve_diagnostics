package com.evehealthcare.diagnostics;

import com.evehealthcare.diagnostics.repository.PaymentRepository;
import com.evehealthcare.diagnostics.repository.WebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookApiTest extends BaseApiTest {
    @Autowired PaymentRepository paymentRepository;
    @Autowired WebhookEventRepository eventRepository;

    private record Paid(String token, long bookingId, String paymentRef) {}

    private Paid bookAndPay(String outcome) throws Exception {
        Catalog c = createCatalog("100.00");
        String user = newUserToken();
        long bookingId = createBooking(user, c);
        String ref = pay(user, bookingId, outcome).get("providerReference").asText();
        return new Paid(user, bookingId, ref);
    }

    @Test
    void rejectsMissingOrInvalidSignature() throws Exception {
        String body = "{\"eventId\":\"e1\",\"paymentReference\":\"pay_x\",\"status\":\"SUCCESS\"}";
        rawWebhook(body, null).andExpect(status().isUnauthorized());
        rawWebhook(body, "deadbeef").andExpect(status().isUnauthorized());
        rawWebhook(body, sign(body + " ")).andExpect(status().isUnauthorized());
        assertEquals(0, eventRepository.findByEventId("e1").map(e -> 1).orElse(0));
    }

    @Test
    void acceptsShaPrefixedSignature() throws Exception {
        Paid p = bookAndPay("FAILED");
        String body = om.writeValueAsString(java.util.Map.of(
                "eventId", uniqueEventId(), "paymentReference", p.paymentRef(), "status", "SUCCESS"));
        rawWebhook(body, "sha256=" + sign(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PROCESSED"));
    }

    @Test
    void rejectsInvalidPayloads() throws Exception {
        String bad1 = "{\"paymentReference\":\"pay_x\",\"status\":\"SUCCESS\"}";                 // no eventId
        String bad2 = "{\"eventId\":\"e\",\"paymentReference\":\"pay_x\",\"status\":\"MAYBE\"}";  // bad status
        String bad3 = "not json at all";
        rawWebhook(bad1, sign(bad1)).andExpect(status().isBadRequest());
        rawWebhook(bad2, sign(bad2)).andExpect(status().isBadRequest());
        rawWebhook(bad3, sign(bad3)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownPaymentReferenceIsNotFoundAndEventIsNotRecorded() throws Exception {
        String eventId = uniqueEventId();
        webhook(eventId, "pay_does_not_exist", "SUCCESS").andExpect(status().isNotFound());
        assertEquals(false, eventRepository.existsByEventId(eventId));   // rolled back, so a retry can succeed later
    }

    @Test
    void replayedEventIsIdempotent() throws Exception {
        Paid p = bookAndPay("FAILED");
        assertEquals("FAILED", bookingStatus(p.token(), p.bookingId()));

        String eventId = uniqueEventId();
        long payments = paymentRepository.count();
        long events = eventRepository.count();

        webhook(eventId, p.paymentRef(), "SUCCESS").andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PROCESSED"));
        assertEquals("CONFIRMED", bookingStatus(p.token(), p.bookingId()));

        for (int i = 0; i < 3; i++) {
            webhook(eventId, p.paymentRef(), "SUCCESS").andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("DUPLICATE"));
        }

        assertEquals("CONFIRMED", bookingStatus(p.token(), p.bookingId()));
        assertEquals(payments, paymentRepository.count(), "no duplicate payments");
        assertEquals(events + 1, eventRepository.count(), "event stored exactly once");
    }

    @Test
    void duplicateEventIdWithDifferentBodyIsStillADuplicate() throws Exception {
        Paid p = bookAndPay("FAILED");
        String eventId = uniqueEventId();
        webhook(eventId, p.paymentRef(), "SUCCESS").andExpect(jsonPath("$.result").value("PROCESSED"));
        // same id, contradictory content: first delivery wins, nothing changes
        webhook(eventId, p.paymentRef(), "FAILED").andExpect(jsonPath("$.result").value("DUPLICATE"));
        assertEquals("CONFIRMED", bookingStatus(p.token(), p.bookingId()));
    }

    @Test
    void differentEventWithSameStatusIsNoChange() throws Exception {
        Paid p = bookAndPay("SUCCESS");
        webhook(uniqueEventId(), p.paymentRef(), "SUCCESS").andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NO_CHANGE"));
        assertEquals("CONFIRMED", bookingStatus(p.token(), p.bookingId()));
    }

    @Test
    void successCannotBeDowngradedByLaterFailedEvent() throws Exception {
        Paid p = bookAndPay("SUCCESS");
        webhook(uniqueEventId(), p.paymentRef(), "FAILED").andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("IGNORED"));
        assertEquals("CONFIRMED", bookingStatus(p.token(), p.bookingId()));
    }

    @Test
    void failedEventOnFailedPaymentIsNoChange() throws Exception {
        Paid p = bookAndPay("FAILED");
        webhook(uniqueEventId(), p.paymentRef(), "FAILED").andExpect(jsonPath("$.result").value("NO_CHANGE"));
        assertEquals("FAILED", bookingStatus(p.token(), p.bookingId()));
    }

    @Test
    void lateSuccessForCancelledBookingIsIgnored() throws Exception {
        Paid p = bookAndPay("FAILED");
        post("/bookings/" + p.bookingId() + "/cancel", p.token(), null).andExpect(status().isOk());
        webhook(uniqueEventId(), p.paymentRef(), "SUCCESS").andExpect(jsonPath("$.result").value("IGNORED"));
        assertEquals("CANCELLED", bookingStatus(p.token(), p.bookingId()));
    }

    @Test
    void lateSuccessForAnOldFailedPaymentCannotDoublePay() throws Exception {
        Paid first = bookAndPay("FAILED");                                   // attempt 1 fails
        pay(first.token(), first.bookingId(), "SUCCESS");                    // retry succeeds -> CONFIRMED
        webhook(uniqueEventId(), first.paymentRef(), "SUCCESS").andExpect(jsonPath("$.result").value("IGNORED"));
        assertEquals("CONFIRMED", bookingStatus(first.token(), first.bookingId()));
    }
}
