package com.evehealthcare.diagnostics.web;

import com.evehealthcare.diagnostics.dto.WebhookResponse;
import com.evehealthcare.diagnostics.service.WebhookService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payment webhook")
@RestController
@RequestMapping("/payments/webhook")
@RequiredArgsConstructor
public class WebhookController {
    private final WebhookService webhookService;


    @PostMapping({"", "/"})
    public WebhookResponse receive(
            @RequestBody String rawBody,
            @Parameter(description = "Hex HMAC-SHA256 of the raw body using the shared webhook secret (optional 'sha256=' prefix).")
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        return webhookService.handle(rawBody, signature);
    }
}
