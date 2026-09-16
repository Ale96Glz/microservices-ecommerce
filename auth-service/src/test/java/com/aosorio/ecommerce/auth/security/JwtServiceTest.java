package com.aosorio.ecommerce.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "clave-secreta-suficientemente-larga-para-hmac-sha-256";

    private final JwtService jwtService = new JwtService(SECRET, 3_600_000L);

    @Test
    void generaTokenQuePuedeLeerseConElMismoSecreto() {
        String token = jwtService.generateToken(99L, "caro@example.com", "USER");

        Claims claims = Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("99");
        assertThat(claims.get("email", String.class)).isEqualTo("caro@example.com");
        assertThat(claims.get("rol", String.class)).isEqualTo("USER");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void preservaElRolAdminEnLosClaims() {
        String token = jwtService.generateToken(5L, "root@example.com", "ADMIN");

        Claims claims = Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.get("rol", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void generaTokenQueExpiraSegunLaConfiguracion() {
        JwtService corto = new JwtService(SECRET, -1_000L);
        String token = corto.generateToken(1L, "x@example.com", "USER");

        assertThatThrownBy(() -> Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token))
                .isInstanceOf(JwtException.class);
    }
}