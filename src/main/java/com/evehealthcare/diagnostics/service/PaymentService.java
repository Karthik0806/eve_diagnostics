package com.evehealthcare.diagnostics.service;

import com.evehealthcare.diagnostics.config.AppProperties;
import com.evehealthcare.diagnostics.domain.Booking;
import com.evehealthcare.diagnostics.domain.BookingStatus;
import com.evehealthcare.diagnostics.domain.Payment;
import com.evehealthcare.diagnostics.domain.PaymentStatus;
import com.evehealthcare.diagnostics.dto.PaymentRequest;
import com.evehealthcare.diagnostics.dto.PaymentResponse;
import com.evehealthcare.diagnostics.repository.BookingRepository;
import com.evehealthcare.diagnostics.repository.PaymentRepository;
import com.evehealthcare.diagnostics.security.AuthUser;
import com.evehealthcare.diagnostics.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final AppProperties props;

    public record PayOutcome(PaymentResponse payment, boolean replayed) {}


    @Transactional
    public PayOutcome pay(AuthUser user, PaymentRequest req, String idempotencyKey) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
            throw ApiException.badRequest("Idempotency-Key must be between 1 and 100 characters");
        }

        Booking booking = bookings.findByIdForUpdate(req.bookingId())
                .filter(b -> b.getUser().getId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Booking " + req.bookingId() + " not found"));

        if (idempotencyKey != null) {
            Optional<Payment> existing = payments.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Payment p = existing.get();
                if (!p.getBooking().getId().equals(booking.getId())) {
                    throw ApiException.conflict("Idempotency-Key was already used for a different booking");
                }
                return new PayOutcome(PaymentResponse.from(p), true);
            }
        }

        switch (booking.getStatus()) {
            case CONFIRMED -> throw ApiException.conflict("Booking is already paid and confirmed");
            case CANCELLED -> throw ApiException.conflict("Booking is cancelled and cannot be paid");
            default -> { /* PENDING, or FAILED (retry after a failed payment) */ }
        }

        PaymentStatus outcome = req.simulatedOutcome() != null ? req.simulatedOutcome()
                : (ThreadLocalRandom.current().nextDouble() < props.paymentSim().successRate()
                        ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getAmount());
        payment.setStatus(outcome);
        payment.setProviderReference("pay_" + UUID.randomUUID().toString().replace("-", ""));
        payment.setIdempotencyKey(idempotencyKey);
        if (outcome == PaymentStatus.FAILED) {
            payment.setFailureReason("Simulated payment failure: card declined");
        }
        booking.setStatus(outcome == PaymentStatus.SUCCESS ? BookingStatus.CONFIRMED : BookingStatus.FAILED);

        Payment saved = payments.saveAndFlush(payment);
        bookings.saveAndFlush(booking);
        log.info("Payment processed paymentId={} bookingId={} status={} ref={}",
                saved.getId(), booking.getId(), outcome, saved.getProviderReference());
        return new PayOutcome(PaymentResponse.from(saved), false);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(AuthUser user, Long id) {
        return payments.findByIdAndBookingUserId(id, user.id())
                .map(PaymentResponse::from)
                .orElseThrow(() -> ApiException.notFound("Payment " + id + " not found"));
    }
}
