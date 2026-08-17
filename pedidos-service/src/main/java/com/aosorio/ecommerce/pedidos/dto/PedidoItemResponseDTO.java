package com.aosorio.ecommerce.pedidos.dto;

import java.math.BigDecimal;

public record PedidoItemResponseDTO(
        Long productoId,
        String nombreProducto,
        BigDecimal precioUnitario,
        int cantidad,
        BigDecimal subtotal
) {
}
