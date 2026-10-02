package com.evehealthcare.diagnostics.service;

import com.evehealthcare.diagnostics.config.AppProperties;
import com.evehealthcare.diagnostics.dto.WebhookRequest;
import com.evehealthcare.diagnostics.dto.WebhookResponse;
import com.evehealthcare.diagnostics.repository.WebhookEventRepository;
import com.evehealthcare.diagnostics.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {
    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final WebhookProcessor processor;
    private final WebhookEventRepository events;

    public WebhookResponse handle(String rawBody, String signatureHeader) {
        verifySignature(rawBody, signatureHeader);
        WebhookRequest req = parseAndValidate(rawBody);
        try {
            return processor.process(req, rawBody);
        } catch (DataIntegrityViolationException e) {
            // Lost a race against another delivery of the same event: it is a duplicate, not an error.
            return events.findByEventId(req.eventId())
                    .map(ev -> {
                        log.info("Concurrent duplicate webhook eventId={}", req.eventId());
                        return WebhookResponse.duplicate(req.eventId(), ev.getOutcome());
                    })
                    .orElseThrow(() -> e);
        }
    }

    private void verifySignature(String rawBody, String header) {
        if (!props.webhook().signatureRequired()) {
            return;
        }
        if (header == null || header.isBlank()) {
            throw ApiException.unauthorized("Missing X-Webhook-Signature header");
        }
        String provided = header.trim();
        if (provided.startsWith("sha256=")) {
            provided = provided.substring("sha256=".length());
        }
        byte[] expected = hmacHex(rawBody).getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided.toLowerCase().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {     // constant-time comparison
            log.warn("Rejected webhook with invalid signature");
            throw ApiException.unauthorized("Invalid webhook signature");
        }
    }

    private String hmacHex(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.webhook().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private WebhookRequest parseAndValidate(String rawBody) {
        WebhookRequest req;
        try {
            req = objectMapper.readValue(rawBody, WebhookRequest.class);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Malformed webhook payload (expected JSON with eventId, paymentReference, status=SUCCESS|FAILED)");
        }
        if (req == null) {
            throw ApiException.badRequest("Webhook payload must not be empty");
        }
        Set<ConstraintViolation<WebhookRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw ApiException.badRequest("Invalid webhook payload: " + detail);
        }
        return req;
    }
}
