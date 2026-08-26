package com.aosorio.ecommerce.events;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedEvent(
        Long pedidoId,
        Long usuarioId,
        BigDecimal total,
        Instant creadoEn
) {
}
