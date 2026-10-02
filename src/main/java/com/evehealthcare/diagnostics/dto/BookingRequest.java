package com.evehealthcare.diagnostics.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BookingRequest(
        @NotNull Long centreId,
        @NotNull Long testId,
        @NotNull @Future(message = "must be in the future") Instant appointmentTime) {
}
