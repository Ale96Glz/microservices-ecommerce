package com.aosorio.ecommerce.notificaciones.dto;

import java.time.LocalDateTime;

public record NotificacionResponseDTO(
        Long id,
        Long usuarioId,
        String tipo,
        String mensaje,
        Long referenciaId,
        boolean leida,
        LocalDateTime fechaCreacion
) {
}
