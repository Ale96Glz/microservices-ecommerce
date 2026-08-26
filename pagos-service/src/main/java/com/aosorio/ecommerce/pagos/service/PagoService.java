package com.aosorio.ecommerce.pagos.service;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.pagos.dto.PageResponseDTO;
import com.aosorio.ecommerce.pagos.dto.PagoRequestDTO;
import com.aosorio.ecommerce.pagos.dto.PagoResponseDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PagoService {
    PagoResponseDTO procesar(PagoRequestDTO request);

    PagoResponseDTO procesarDesdeEvento(OrderCreatedEvent event);

    PagoResponseDTO obtenerPorId(Long id);

    PagoResponseDTO obtenerPorPedido(Long pedidoId);

    List<PagoResponseDTO> obtenerTodos();

    List<PagoResponseDTO> obtenerPorUsuario(Long usuarioId);

    PageResponseDTO<PagoResponseDTO> obtenerTodosPaginado(Pageable pageable);
}
