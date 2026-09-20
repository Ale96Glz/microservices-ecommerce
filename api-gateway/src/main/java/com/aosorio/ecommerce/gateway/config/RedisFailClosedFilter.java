package com.aosorio.ecommerce.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * En {@code prod}, si Redis no responde el gateway no deja pasar tráfico de API
 * (fail-closed, ADR-0010). Spring Cloud Gateway 4.1 fail-open en el rate
 * limiter; este filtro lo corrige. {@code /actuator} sigue accesible para probes.
 */
@Component
@Profile("prod")
public class RedisFailClosedFilter implements GlobalFilter, Ordered {

    static final String REDIS_PROBE_KEY = "gateway:redis:fail-closed";
    private static final Duration REDIS_PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final Logger log = LoggerFactory.getLogger(RedisFailClosedFilter.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisFailClosedFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        return redisTemplate.hasKey(REDIS_PROBE_KEY)
                .timeout(REDIS_PROBE_TIMEOUT)
                .thenReturn(Boolean.TRUE)
                .onErrorResume(ex -> unavailable(exchange, ex).then(Mono.empty()))
                .flatMap(ok -> chain.filter(exchange));
    }

    private static Mono<Void> unavailable(ServerWebExchange exchange, Throwable ex) {
        log.warn("Redis no disponible; API fail-closed: {}", ex.toString());
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"status\":503,\"error\":\"Service Unavailable\",\"message\":\"Rate limiter no disponible\"}"
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
