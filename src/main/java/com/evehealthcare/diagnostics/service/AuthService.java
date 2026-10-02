package com.evehealthcare.diagnostics.service;

import com.evehealthcare.diagnostics.domain.Role;
import com.evehealthcare.diagnostics.domain.User;
import com.evehealthcare.diagnostics.dto.LoginRequest;
import com.evehealthcare.diagnostics.dto.SignupRequest;
import com.evehealthcare.diagnostics.dto.TokenResponse;
import com.evehealthcare.diagnostics.dto.UserResponse;
import com.evehealthcare.diagnostics.repository.UserRepository;
import com.evehealthcare.diagnostics.security.JwtService;
import com.evehealthcare.diagnostics.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserResponse signup(SignupRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(req.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        User saved = users.saveAndFlush(user);
        log.info("User registered userId={}", saved.getId());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmail(normalize(req.email()))
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> {
                    log.warn("Failed login attempt");
                    return ApiException.unauthorized("Invalid email or password");   // same message for both cases
                });
        return new TokenResponse(jwtService.generate(user), "Bearer", jwtService.ttlSeconds());
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
