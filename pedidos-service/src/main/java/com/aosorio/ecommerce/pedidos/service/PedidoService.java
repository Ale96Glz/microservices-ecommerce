package com.aosorio.ecommerce.pedidos.service;

import com.aosorio.ecommerce.pedidos.dto.PageResponseDTO;
import com.aosorio.ecommerce.pedidos.dto.PedidoRequestDTO;
import com.aosorio.ecommerce.pedidos.dto.PedidoResponseDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PedidoService {
    PedidoResponseDTO crear(Long usuarioId, PedidoRequestDTO pedidoRequestDTO);

    PedidoResponseDTO cancelar(Long id);

    PedidoResponseDTO obtenerPorId(Long id);

    List<PedidoResponseDTO> obtenerTodos();

    List<PedidoResponseDTO> obtenerPorUsuario(Long usuarioId);

    PageResponseDTO<PedidoResponseDTO> obtenerTodosPaginado(Pageable pageable);
}
