package com.aosorio.ecommerce.pedidos.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JwtIdentityRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> identityHeaders;

    public JwtIdentityRequestWrapper(HttpServletRequest request, Claims claims) {
        super(request);
        this.identityHeaders = new LinkedHashMap<>();
        this.identityHeaders.put("X-User-Id", claims.getSubject());
        this.identityHeaders.put("X-User-Email", stringClaim(claims, "email"));
        this.identityHeaders.put("X-User-Rol", stringClaim(claims, "rol"));
    }

    private static String stringClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value != null ? value.toString() : "";
    }

    @Override
    public String getHeader(String name) {
        String identityValue = identityHeaders.get(name);
        return identityValue != null ? identityValue : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (identityHeaders.containsKey(name)) {
            return Collections.enumeration(List.of(identityHeaders.get(name)));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = Collections.list(super.getHeaderNames());
        identityHeaders.keySet().forEach(key -> {
            if (!names.contains(key)) {
                names.add(key);
            }
        });
        return Collections.enumeration(names);
    }
}