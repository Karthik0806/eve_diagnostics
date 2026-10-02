package com.evehealthcare.diagnostics.service;

import com.evehealthcare.diagnostics.domain.*;
import com.evehealthcare.diagnostics.dto.WebhookRequest;
import com.evehealthcare.diagnostics.dto.WebhookResponse;
import com.evehealthcare.diagnostics.repository.BookingRepository;
import com.evehealthcare.diagnostics.repository.PaymentRepository;
import com.evehealthcare.diagnostics.repository.WebhookEventRepository;
import com.evehealthcare.diagnostics.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookProcessor {
    public static final String PROCESSED = "PROCESSED";
    public static final String NO_CHANGE = "NO_CHANGE";
    public static final String IGNORED = "IGNORED";

    private final WebhookEventRepository events;
    private final BookingRepository bookings;
    private final PaymentRepository payments;

    @Transactional
    public WebhookResponse process(WebhookRequest req, String rawBody) {
        Optional<WebhookEvent> seen = events.findByEventId(req.eventId());
        if (seen.isPresent()) {
            return WebhookResponse.duplicate(req.eventId(), seen.get().getOutcome());
        }

        WebhookEvent event = new WebhookEvent();
        event.setEventId(req.eventId());
        event.setPaymentReference(req.paymentReference());
        event.setEventStatus(req.status().name());
        event.setOutcome("RECEIVED");
        event.setPayload(rawBody.length() > 4000 ? rawBody.substring(0, 4000) : rawBody);
        events.saveAndFlush(event);

        // Lock the booking first, then read the payment, so we always act on committed, current state.
        Booking booking = bookings.findByPaymentReferenceForUpdate(req.paymentReference())
                .orElseThrow(() -> ApiException.notFound("Unknown payment reference"));
        Payment payment = payments.findByProviderReference(req.paymentReference())
                .orElseThrow(() -> ApiException.notFound("Unknown payment reference"));

        String outcome;
        String message;
        PaymentStatus current = payment.getStatus();
        PaymentStatus incoming = req.status();

        if (current == incoming) {
            outcome = NO_CHANGE;
            message = "Payment already " + current + "; nothing to update";
        } else if (current == PaymentStatus.SUCCESS) {
            outcome = IGNORED;
            message = "Payment already SUCCESS; a later FAILED event cannot downgrade it";
        } else if (booking.getStatus() == BookingStatus.CANCELLED) {
            outcome = IGNORED;
            message = "Booking is cancelled; payment not applied";
        } else if (payments.existsByBookingIdAndStatus(booking.getId(), PaymentStatus.SUCCESS)) {
            outcome = IGNORED;
            message = "Booking is already paid by another payment";
        } else {
            // FAILED -> SUCCESS (e.g. late settlement reported by the provider)
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setFailureReason(null);
            booking.setStatus(BookingStatus.CONFIRMED);
            payments.saveAndFlush(payment);
            bookings.saveAndFlush(booking);
            outcome = PROCESSED;
            message = "Payment marked SUCCESS and booking CONFIRMED";
        }

        event.setOutcome(outcome);
        log.info("Webhook handled eventId={} paymentRef={} incoming={} outcome={}",
                req.eventId(), req.paymentReference(), incoming, outcome);
        return new WebhookResponse(req.eventId(), outcome, message);
    }
}
