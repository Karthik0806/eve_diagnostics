package com.evehealthcare.diagnostics.dto;

import com.evehealthcare.diagnostics.domain.BookingStatus;
import com.evehealthcare.diagnostics.domain.Payment;
import com.evehealthcare.diagnostics.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long bookingId, BigDecimal amount, PaymentStatus status,
                              String providerReference, String failureReason,
                              BookingStatus bookingStatus, Instant createdAt) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getBooking().getId(), p.getAmount(), p.getStatus(),
                p.getProviderReference(), p.getFailureReason(), p.getBooking().getStatus(), p.getCreatedAt());
    }
}
