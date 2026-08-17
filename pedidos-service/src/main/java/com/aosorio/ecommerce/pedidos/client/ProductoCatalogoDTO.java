package com.aosorio.ecommerce.pedidos.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductoCatalogoDTO(
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
