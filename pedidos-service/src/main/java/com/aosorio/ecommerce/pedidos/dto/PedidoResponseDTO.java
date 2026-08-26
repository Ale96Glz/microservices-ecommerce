package com.aosorio.ecommerce.pedidos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponseDTO(
        Long id,
        Long usuarioId,
        BigDecimal total,
        String estado,
        List<PedidoItemResponseDTO> items,
        LocalDateTime fechaCreacion
) {
}
