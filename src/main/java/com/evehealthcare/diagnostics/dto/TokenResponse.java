package com.evehealthcare.diagnostics.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
