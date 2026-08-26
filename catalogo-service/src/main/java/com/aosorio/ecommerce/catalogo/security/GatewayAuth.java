package com.aosorio.ecommerce.catalogo.security;

import com.aosorio.ecommerce.catalogo.exception.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;

public final class GatewayAuth {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROL_HEADER = "X-User-Rol";

    private GatewayAuth() {
    }

    public record User(Long id, String rol) {
        public boolean isAdmin() {
            return "ADMIN".equalsIgnoreCase(rol);
        }
    }

    public static User requireUser(HttpServletRequest request) {
        String rawId = request.getHeader(USER_ID_HEADER);
        if (rawId == null || rawId.isBlank()) {
            throw new AccessDeniedException("Falta identidad de usuario (X-User-Id). Usa el api-gateway con JWT.");
        }
        try {
            Long id = Long.valueOf(rawId.trim());
            String rol = request.getHeader(USER_ROL_HEADER);
            return new User(id, rol != null ? rol : "");
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("X-User-Id inválido");
        }
    }

    public static User requireAdmin(HttpServletRequest request) {
        User user = requireUser(request);
        if (!user.isAdmin()) {
            throw new AccessDeniedException("Se requiere rol ADMIN");
        }
        return user;
    }
}
