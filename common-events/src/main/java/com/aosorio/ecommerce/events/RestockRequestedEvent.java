package com.aosorio.ecommerce.events;

import java.time.Instant;

public record RestockRequestedEvent(
        String eventId,
        Long pedidoId,
        Long productoId,
        int cantidad,
        Instant solicitadoEn
) {
}