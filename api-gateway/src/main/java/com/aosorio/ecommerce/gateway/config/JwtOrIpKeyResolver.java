package com.aosorio.ecommerce.gateway.config;

import com.aosorio.ecommerce.gateway.security.JwtValidator;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Clave de rate limit (ADR-0010): {@code jwt:<sub>} si hay Bearer válido;
 * si no {@code ip:<cliente>}. Login/register siguen usando solo IP.
 */
@Component
public class JwtOrIpKeyResolver implements KeyResolver {

    private final JwtValidator jwtValidator;

    public JwtOrIpKeyResolver(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String bearer = extractBearer(exchange.getRequest());
        if (bearer == null) {
            return Mono.just(ipKey(exchange.getRequest()));
        }
        return Mono.fromCallable(() -> jwtValidator.validate(bearer).getSubject())
                .subscribeOn(Schedulers.boundedElastic())
                .filter(sub -> sub != null && !sub.isBlank())
                .map(sub -> "jwt:" + sub)
                .onErrorReturn(ipKey(exchange.getRequest()))
                .defaultIfEmpty(ipKey(exchange.getRequest()));
    }

    static String ipKey(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }
        var remoteAddress = request.getRemoteAddress();
        String ip = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
        return "ip:" + ip;
    }

    private static String extractBearer(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
