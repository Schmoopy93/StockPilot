package com.stockpilot.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimiter rateLimiter;
    private final AuthClientAddressResolver addressResolver;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(AuthRateLimiter rateLimiter, AuthClientAddressResolver addressResolver,
                               ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.addressResolver = addressResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Policy policy = policyFor(request.getMethod(), request.getRequestURI(), request.getContextPath());
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientAddress = addressResolver.resolve(request);
        if (clientAddress == null || clientAddress.isBlank()) {
            throw new ServletException("Client address is unavailable for authentication rate limiting");
        }

        AuthRateLimiter.Result result = rateLimiter.tryAcquire(
                policy.endpoint(), clientAddress, policy.limit(), policy.windowSeconds());
        response.setHeader("X-RateLimit-Limit", Integer.toString(result.limit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(result.remaining()));
        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(result.retryAfterSeconds()));
            response.setHeader("Cache-Control", "no-store");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    Map.of("status", 429, "message", "Previše zahteva. Pokušajte ponovo kasnije."));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Policy policyFor(String method, String requestUri, String contextPath) {
        String path = requestUri.substring(contextPath.length());
        if (!HttpMethod.POST.matches(method)) {
            return null;
        }
        return switch (path) {
            case "/api/auth/login" -> new Policy("login", 10, 60);
            case "/api/auth/register" -> new Policy("register", 5, 3600);
            case "/api/auth/forgot-password" -> new Policy("forgot-password", 5, 3600);
            case "/api/auth/resend-verification" -> new Policy("resend-verification", 5, 3600);
            case "/api/auth/verify-email", "/api/auth/verify-email/form" ->
                    new Policy("verify-email", 10, 900);
            case "/api/auth/reset-password", "/api/auth/reset-password/form" ->
                    new Policy("reset-password", 5, 900);
            default -> null;
        };
    }

    private record Policy(String endpoint, int limit, int windowSeconds) {}
}
