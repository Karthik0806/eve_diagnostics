package com.evehealthcare.diagnostics.dto;

import com.evehealthcare.diagnostics.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;

/**
 * @param simulatedOutcome optional; forces the mock provider to return SUCCESS or FAILED.
 *                         When omitted the outcome is random (see app.payment-sim.success-rate).
 */
public record PaymentRequest(@NotNull Long bookingId, PaymentStatus simulatedOutcome) {
}
