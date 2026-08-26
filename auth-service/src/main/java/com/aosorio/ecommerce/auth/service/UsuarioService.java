package com.aosorio.ecommerce.auth.service;

import com.aosorio.ecommerce.auth.dto.PageResponseDTO;
import com.aosorio.ecommerce.auth.dto.UsuarioRequestDTO;
import com.aosorio.ecommerce.auth.dto.UsuarioResponseDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UsuarioService {
    UsuarioResponseDTO actualizar(Long id, UsuarioRequestDTO request);

    UsuarioResponseDTO actualizarRol(Long id, String rol);

    void eliminar(Long id);

    UsuarioResponseDTO obtenerPorId(Long id);

    List<UsuarioResponseDTO> obtenerTodos();

    PageResponseDTO<UsuarioResponseDTO> obtenerTodosPaginado(Pageable pageable);
}
