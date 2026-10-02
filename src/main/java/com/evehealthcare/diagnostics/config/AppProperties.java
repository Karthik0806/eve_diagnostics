package com.evehealthcare.diagnostics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Webhook webhook, PaymentSim paymentSim, Admin admin,
                            Seed seed, RateLimit rateLimit) {
    public record Jwt(String secret, long expirationMinutes) {}
    public record Webhook(String secret, boolean signatureRequired) {}
    public record PaymentSim(double successRate) {}
    public record Admin(String email, String password) {}
    public record Seed(boolean sampleData) {}
    public record RateLimit(boolean enabled, int authPerMinute) {}
}
