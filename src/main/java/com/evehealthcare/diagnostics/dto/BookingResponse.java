package com.evehealthcare.diagnostics.dto;

import com.evehealthcare.diagnostics.domain.Booking;
import com.evehealthcare.diagnostics.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(Long id, Long centreId, String centreName, Long testId, String testName,
                              Instant appointmentTime, BigDecimal amount, BookingStatus status,
                              Instant createdAt, Instant updatedAt) {
    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), b.getCentre().getId(), b.getCentre().getName(),
                b.getTest().getId(), b.getTest().getName(), b.getAppointmentTime(), b.getAmount(),
                b.getStatus(), b.getCreatedAt(), b.getUpdatedAt());
    }
}
