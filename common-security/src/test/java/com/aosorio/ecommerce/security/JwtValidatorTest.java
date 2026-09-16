package com.aosorio.ecommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidatorTest {

    private static final String SECRET = "clave-secreta-suficientemente-larga-para-hmac-sha-256";

    private final JwtValidator validator = new JwtValidator(SECRET);

    private String buildToken(String subject, String email, String rol, Date exp) {
        return Jwts.builder()
                .subject(subject)
                .claim("email", email)
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(exp)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void validarTokenValidoDevuelveClaims() {
        String token = buildToken("1", "user@example.com", "USER",
                new Date(System.currentTimeMillis() + 60_000));

        Claims claims = validator.validate(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("rol", String.class)).isEqualTo("USER");
    }

    @Test
    void tokenFirmadoConOtraClaveEsRechazado() {
        SecretKey otraClave = Keys.hmacShaKeyFor(
                ("otra-clave-suficientemente-larga-para-hmac-sha-256x").getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otraClave)
                .compact();

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenExpiradoEsRechazado() {
        String token = buildToken("1", "user@example.com", "USER",
                new Date(System.currentTimeMillis() - 60_000));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(ExpiredJwtException.class)
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenMalformadoEsRechazado() {
        assertThatThrownBy(() -> validator.validate("no-es-un-jwt"))
                .isInstanceOf(JwtException.class);
    }
}