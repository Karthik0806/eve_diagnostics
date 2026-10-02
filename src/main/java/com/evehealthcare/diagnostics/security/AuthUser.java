package com.evehealthcare.diagnostics.security;

import com.evehealthcare.diagnostics.domain.Role;

/** The authenticated principal, rebuilt from JWT claims on every request (no DB hit). */
public record AuthUser(Long id, String email, Role role) {
}
