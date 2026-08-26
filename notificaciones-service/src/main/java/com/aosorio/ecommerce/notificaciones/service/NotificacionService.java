package com.aosorio.ecommerce.notificaciones.service;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import com.aosorio.ecommerce.notificaciones.dto.NotificacionRequestDTO;
import com.aosorio.ecommerce.notificaciones.dto.NotificacionResponseDTO;
import com.aosorio.ecommerce.notificaciones.dto.PageResponseDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificacionService {
    NotificacionResponseDTO crear(NotificacionRequestDTO request);

    NotificacionResponseDTO registrarPedidoCreado(OrderCreatedEvent event);

    NotificacionResponseDTO registrarPagoProcesado(PaymentProcessedEvent event);

    NotificacionResponseDTO obtenerPorId(Long id);

    List<NotificacionResponseDTO> obtenerTodos();

    List<NotificacionResponseDTO> obtenerPorUsuario(Long usuarioId);

    List<NotificacionResponseDTO> obtenerNoLeidasPorUsuario(Long usuarioId);

    PageResponseDTO<NotificacionResponseDTO> obtenerTodosPaginado(Pageable pageable);

    NotificacionResponseDTO marcarComoLeida(Long id);
}
