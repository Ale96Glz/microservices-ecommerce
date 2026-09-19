package com.aosorio.ecommerce.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoResponseDTO(
        Long id,
        Long pedidoId,
        Long usuarioId,
        BigDecimal monto,
        String estado,
        String motivoRechazo,
        Integer intento,
        LocalDateTime fechaProcesado
) {
}
