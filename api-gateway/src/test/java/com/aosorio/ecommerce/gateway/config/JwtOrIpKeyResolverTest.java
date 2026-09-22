package com.aosorio.ecommerce.gateway.config;

import com.aosorio.ecommerce.gateway.security.JwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtOrIpKeyResolverTest {

    private static final String SECRET = "clave-secreta-suficientemente-larga-para-hmac-sha-256";

    private final JwtOrIpKeyResolver resolver = new JwtOrIpKeyResolver(new JwtValidator(SECRET));

    @Test
    void usaSubDelJwtCuandoElBearerEsValido() {
        String token = token("42");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/pedido")
                        .remoteAddress(new InetSocketAddress("10.0.0.8", 443))
                        .header("Authorization", "Bearer " + token)
                        .build());

        assertThat(resolver.resolve(exchange).block()).isEqualTo("jwt:42");
    }

    @Test
    void caeAIpSiNoHayAuthorization() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/producto")
                        .remoteAddress(new InetSocketAddress("10.0.0.8", 443))
                        .build());

        assertThat(resolver.resolve(exchange).block()).isEqualTo("ip:10.0.0.8");
    }

    @Test
    void usaXForwardedForCuandoElTokenEsInvalido() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/producto")
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
                        .header("Authorization", "Bearer esto-no-es-un-jwt")
                        .build());

        assertThat(resolver.resolve(exchange).block()).isEqualTo("ip:203.0.113.9");
    }

    private static String token(String subject) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}
