package com.evehealthcare.diagnostics.dto;

import com.evehealthcare.diagnostics.domain.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WebhookRequest(
        @NotBlank @Size(max = 100) String eventId,
        @NotBlank @Size(max = 64) String paymentReference,
        @NotNull PaymentStatus status) {
}
