package com.aosorio.ecommerce.pedidos.security;

import com.aosorio.ecommerce.pedidos.exception.AccessDeniedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
@ConditionalOnProperty(name = "jwt.filter.enabled", havingValue = "true", matchIfMissing = true)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final List<String> DEFAULT_PUBLIC_PATHS = List.of(
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/swagger-ui",
            "/v3/api-docs",
            "/h2-console"
    );

    private final JwtValidator jwtValidator;
    private final List<String> publicPaths;
    private final List<String> publicGetPrefixes;

    public JwtAuthFilter(
            JwtValidator jwtValidator,
            @Value("${jwt.filter.public-paths:}") String publicPaths,
            @Value("${jwt.filter.public-get-prefixes:}") String publicGetPrefixes
    ) {
        this.jwtValidator = jwtValidator;
        this.publicPaths = split(publicPaths);
        this.publicGetPrefixes = split(publicGetPrefixes);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (HttpMethod.OPTIONS.name().equals(request.getMethod()) || isPublic(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Falta el header Authorization Bearer");
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtValidator.validate(token);
            filterChain.doFilter(new JwtIdentityRequestWrapper(request, claims), response);
        } catch (JwtException | IllegalArgumentException | AccessDeniedException ex) {
            log.warn("Token inválido en {}: {}", path, ex.getMessage());
            writeUnauthorized(response, "Token inválido o expirado");
        }
    }

    private boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (DEFAULT_PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return true;
        }
        if (publicPaths.stream().anyMatch(path::startsWith)) {
            return true;
        }
        if (HttpMethod.GET.matches(request.getMethod())) {
            return publicGetPrefixes.stream()
                    .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
        }
        return false;
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        String body = "{\"timestamp\":\"" + Instant.now()
                + "\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
        response.getWriter().write(body);
        response.getWriter().flush();
    }
}