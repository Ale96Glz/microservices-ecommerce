package com.aosorio.ecommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET = "clave-secreta-suficientemente-larga-para-hmac-sha-256";

    private JwtValidator validator;
    private JwtAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        validator = new JwtValidator(SECRET);
        filter = new JwtAuthFilter(
                validator,
                List.of("/api/v1/abierta"),
                List.of("/api/v1/productos")
        );
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getRequestURI()).thenReturn("/api/v1/pedido");
        when(request.getMethod()).thenReturn("GET");
    }

    private String tokenValido() {
        return Jwts.builder()
                .subject("10")
                .claim("email", "a@b.com")
                .claim("rol", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void rutaPublicaPorDefectoNoRequiereToken() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rutaPublicaConfiguradaPorPrefijoNoRequiereToken() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/abierta/detalle");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void metodoOptionsPasaSinToken() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void getPublicoConPrefijoPermitidoPasaSinToken() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/productos/5");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void postPublicoConPrefijoPermitidoNoPasaSinToken() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/productos/5");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void ausenciaDeAuthorizationDevuelve401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void headerQueNoEmpiezaConBearerDevuelve401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void tokenInvalidoDevuelve401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-roto");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void tokenValidoContinuaLaCadenaConWrapperDeIdentidad() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + tokenValido());

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(response));
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void tokenValidoEnriqueceLaRequestConHeadersDeIdentidad() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + tokenValido());
        org.mockito.ArgumentCaptor<jakarta.servlet.http.HttpServletRequest> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(jakarta.servlet.http.HttpServletRequest.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(wrapperCaptor.capture(), org.mockito.ArgumentMatchers.any());
        jakarta.servlet.http.HttpServletRequest wrappado = wrapperCaptor.getValue();
        assertThat(wrappado).isInstanceOf(JwtIdentityRequestWrapper.class);
        assertThat(wrappado.getHeader("X-User-Id")).isEqualTo("10");
        assertThat(wrappado.getHeader("X-User-Email")).isEqualTo("a@b.com");
        assertThat(wrappado.getHeader("X-User-Rol")).isEqualTo("USER");
    }

    @Test
    void asListSeparaYRecortaValores() {
        assertThat(JwtAuthFilter.asList(" /p,  /q ")).containsExactly("/p", "/q");
        assertThat(JwtAuthFilter.asList("")).isEmpty();
        assertThat(JwtAuthFilter.asList(null)).isEmpty();
        assertThat(JwtAuthFilter.asList("a,,b")).containsExactly("a", "b");
    }
}