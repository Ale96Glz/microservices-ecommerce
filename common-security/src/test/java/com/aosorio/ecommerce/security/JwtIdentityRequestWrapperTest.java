package com.aosorio.ecommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtIdentityRequestWrapperTest {

    private static final Claims CLAIMS = Jwts.claims()
            .subject("42")
            .add("email", "ana@example.com")
            .add("rol", "ADMIN")
            .build();

    private HttpServletRequest original;
    private JwtIdentityRequestWrapper wrapper;

    @BeforeEach
    void setUp() {
        original = mock(HttpServletRequest.class);
        Map<String, String> headersExistentes = Map.of("Authorization", "Bearer token-xyz", "X-Edad", "30");
        when(original.getHeader("Authorization")).thenReturn(headersExistentes.get("Authorization"));
        when(original.getHeaders("Authorization")).thenReturn(Collections.enumeration(List.of("Bearer token-xyz")));
        when(original.getHeaderNames()).thenReturn(Collections.enumeration(List.of("Authorization", "X-Edad")));
        when(original.getHeader("X-Edad")).thenReturn(headersExistentes.get("X-Edad"));
        wrapper = new JwtIdentityRequestWrapper(original, CLAIMS);
    }

    @Test
    void exponeLosHeadersDeIdentidadDelToken() {
        assertThat(wrapper.getHeader("X-User-Id")).isEqualTo("42");
        assertThat(wrapper.getHeader("X-User-Email")).isEqualTo("ana@example.com");
        assertThat(wrapper.getHeader("X-User-Rol")).isEqualTo("ADMIN");
    }

    @Test
    void losClaimsSinEmailORolNoDejanElHeaderVacio() {
        Claims soloSubject = Jwts.claims().subject("7").build();
        JwtIdentityRequestWrapper sinData = new JwtIdentityRequestWrapper(original, soloSubject);

        assertThat(sinData.getHeader("X-User-Email")).isEmpty();
        assertThat(sinData.getHeader("X-User-Rol")).isEmpty();
        assertThat(sinData.getHeader("X-User-Id")).isEqualTo("7");
    }

    @Test
    void preservaLosHeadersOriginalesQueNoSonDeIdentidad() {
        assertThat(wrapper.getHeader("Authorization")).isEqualTo("Bearer token-xyz");
        assertThat(wrapper.getHeader("X-Edad")).isEqualTo("30");
    }

    @Test
    void getHeadersParaNombreDeIdentidadDevuelveUnSoloValor() {
        Enumeration<String> valores = wrapper.getHeaders("X-User-Id");
        assertThat(Collections.list(valores)).containsExactly("42");
    }

    @Test
    void getHeadersParaNombreAjenoDelegaEnElOriginal() {
        when(original.getHeaders("X-Edad")).thenReturn(Collections.enumeration(List.of("30")));
        assertThat(Collections.list(wrapper.getHeaders("X-Edad"))).containsExactly("30");
    }

    @Test
    void getHeaderNamesIncluyeLosDeIdentidad() {
        List<String> nombres = Collections.list(wrapper.getHeaderNames());
        assertThat(nombres).contains("X-User-Id", "X-User-Email", "X-User-Rol", "Authorization", "X-Edad");
    }
}