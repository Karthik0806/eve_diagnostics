package com.evehealthcare.diagnostics.dto;

import com.evehealthcare.diagnostics.domain.Role;
import com.evehealthcare.diagnostics.domain.User;

import java.time.Instant;

public record UserResponse(Long id, String email, String fullName, Role role, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getCreatedAt());
    }
}
