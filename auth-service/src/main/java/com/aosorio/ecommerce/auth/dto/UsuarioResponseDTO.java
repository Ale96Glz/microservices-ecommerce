package com.aosorio.ecommerce.auth.dto;

import java.time.LocalDateTime;

public record UsuarioResponseDTO(
        Long id,
        String nombre,
        String email,
        String rol,
        LocalDateTime fechaCreacion
) {
}
