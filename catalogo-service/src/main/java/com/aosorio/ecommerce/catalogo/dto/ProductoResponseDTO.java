package com.aosorio.ecommerce.catalogo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductoResponseDTO(
        Long id,
        String nombre,
        String descipcion,
        BigDecimal precio,
        int stock,
        String estado,
        String categoria,
        LocalDateTime fechaCreacion
) {
}
