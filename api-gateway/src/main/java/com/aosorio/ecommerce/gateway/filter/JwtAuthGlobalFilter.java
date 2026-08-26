package com.aosorio.ecommerce.gateway.filter;

import com.aosorio.ecommerce.gateway.security.JwtValidator;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "gateway.security.enabled", havingValue = "true", matchIfMissing = true)
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login"
    );

    private static final List<String> PUBLIC_GET_PREFIXES = List.of(
            "/api/v1/producto",
            "/api/v1/categoria"
    );

    private final JwtValidator jwtValidator;

    public JwtAuthGlobalFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isPublic(request.getMethod(), path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Falta el header Authorization Bearer");
        }

        String token = authHeader.substring(7);

        return Mono.fromCallable(() -> jwtValidator.validate(token))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(claims -> chain.filter(exchange.mutate().request(enrichRequest(request, claims)).build()))
                .onErrorResume(ex -> {
                    log.warn("Token inválido en {}: {}", path, ex.getMessage());
                    return unauthorized(exchange, "Token inválido o expirado");
                });
    }

    private ServerHttpRequest enrichRequest(ServerHttpRequest request, Claims claims) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Rol");
                })
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Email", stringClaim(claims, "email"))
                .header("X-User-Rol", stringClaim(claims, "rol"))
                .build();
    }

    private String stringClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value != null ? value.toString() : "";
    }

    private boolean isPublic(HttpMethod method, String path) {
        if (PUBLIC_PATHS.stream().anyMatch(path::equals)) {
            return true;
        }
        if (HttpMethod.GET.equals(method)) {
            return PUBLIC_GET_PREFIXES.stream().anyMatch(prefix ->
                    path.equals(prefix) || path.startsWith(prefix + "/"));
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"timestamp\":\"" + Instant.now()
                + "\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
