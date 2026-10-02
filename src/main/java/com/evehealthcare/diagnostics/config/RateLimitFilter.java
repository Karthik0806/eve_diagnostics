package com.evehealthcare.diagnostics.config;

import com.evehealthcare.diagnostics.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class RateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MS = 60_000L;
    private static final Set<String> LIMITED_PATHS = Set.of("/auth/login", "/auth/signup");

    private static final class Window {
        final long start;
        int count;
        Window(long start) { this.start = start; }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(int limit, ObjectMapper objectMapper) {
        this.limit = limit;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= WINDOW_MS);
        }
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        boolean allowed;
        synchronized (windows) {
            Window w = windows.compute(key, (k, old) -> (old == null || now - old.start >= WINDOW_MS) ? new Window(now) : old);
            w.count++;
            allowed = w.count <= limit;
        }
        if (!allowed) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 429,
                    "Too Many Requests", "Rate limit exceeded, retry later", request.getRequestURI(), null));
            return;
        }
        chain.doFilter(request, response);
    }
}
