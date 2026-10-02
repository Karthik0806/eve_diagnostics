package com.evehealthcare.diagnostics.web;

import com.evehealthcare.diagnostics.dto.PaymentRequest;
import com.evehealthcare.diagnostics.dto.PaymentResponse;
import com.evehealthcare.diagnostics.security.AuthUser;
import com.evehealthcare.diagnostics.service.PaymentService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payments")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping({"", "/"})
    public ResponseEntity<PaymentResponse> pay(
            @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody PaymentRequest req,
            @Parameter(description = "Optional. Re-sending the same key returns the original payment instead of paying again.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PaymentService.PayOutcome out = paymentService.pay(user, req, idempotencyKey);
        return ResponseEntity.status(out.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(out.payment());
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return paymentService.get(user, id);
    }
}
