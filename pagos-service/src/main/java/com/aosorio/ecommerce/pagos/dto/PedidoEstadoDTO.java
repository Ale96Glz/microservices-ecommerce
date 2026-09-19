package com.aosorio.ecommerce.pagos.dto;

import java.math.BigDecimal;

public record PedidoEstadoDTO(
        Long id,
        Long usuarioId,
        BigDecimal total,
        String estado
) {
}