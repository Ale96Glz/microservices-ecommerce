package com.aosorio.ecommerce.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * {@link KeyResolver} de login/register (ADR-0010): solo IP. El resto de
 * rutas usa {@link JwtOrIpKeyResolver}.
 */
@Configuration
public class GatewayRateLimitConfig {

    @Bean
    public KeyResolver remoteAddressKeyResolver() {
        return exchange -> Mono.just(JwtOrIpKeyResolver.ipKey(exchange.getRequest()));
    }
}