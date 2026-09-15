package com.aosorio.ecommerce.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Define el {@link KeyResolver} usado por el filtro RequestRateLimiter
 * (ADR-0010). Identifica al cliente por su dirección IP.
 *
 * <p>En Kubernetes el Ingress añade {@code X-Forwarded-For}, por lo que se usa
 * su primer valor si está presente; si no, se cae a la IP remota directa
 * (docker compose local).
 */
@Configuration
public class GatewayRateLimitConfig {

    @Bean
    public KeyResolver remoteAddressKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return Mono.just(forwardedFor.split(",")[0].trim());
            }
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            String ip = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
            return Mono.just(ip);
        };
    }
}