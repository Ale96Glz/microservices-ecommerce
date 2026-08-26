package com.aosorio.ecommerce.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentProcessedEvent(
        Long pagoId,
        Long pedidoId,
        Long usuarioId,
        BigDecimal monto,
        String estado,
        Instant procesadoEn
) {
}
