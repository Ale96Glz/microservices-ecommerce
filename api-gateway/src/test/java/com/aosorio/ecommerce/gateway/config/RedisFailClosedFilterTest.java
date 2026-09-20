package com.aosorio.ecommerce.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisFailClosedFilterTest {

    @Test
    void actuatorNoConsultaRedis() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());

        new RedisFailClosedFilter(redis).filter(exchange, chain).block();

        verify(redis, never()).hasKey(any());
        verify(chain).filter(exchange);
    }

    @Test
    void apiDevuelve503SiRedisFalla() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.hasKey(RedisFailClosedFilter.REDIS_PROBE_KEY))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        new RedisFailClosedFilter(redis).filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(any());
    }

    @Test
    void apiContinuaSiRedisResponde() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.hasKey(RedisFailClosedFilter.REDIS_PROBE_KEY)).thenReturn(Mono.just(false));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        new RedisFailClosedFilter(redis).filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
