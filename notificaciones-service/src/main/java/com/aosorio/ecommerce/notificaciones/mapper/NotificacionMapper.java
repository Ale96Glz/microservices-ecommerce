package com.aosorio.ecommerce.notificaciones.mapper;

import com.aosorio.ecommerce.notificaciones.domain.Notificacion;
import com.aosorio.ecommerce.notificaciones.dto.NotificacionResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class NotificacionMapper {

    public NotificacionResponseDTO toResponseDto(Notificacion notificacion) {
        return new NotificacionResponseDTO(
                notificacion.getId(),
                notificacion.getUsuarioId(),
                notificacion.getTipo() != null ? notificacion.getTipo().name() : null,
                notificacion.getMensaje(),
                notificacion.getReferenciaId(),
                notificacion.isLeida(),
                notificacion.getFechaCreacion()
        );
    }
}
