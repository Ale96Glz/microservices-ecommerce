package com.aosorio.ecommerce.catalogo.service;

import com.aosorio.ecommerce.catalogo.dto.CategoriaRequestDTO;
import com.aosorio.ecommerce.catalogo.dto.CategoriaResponseDTO;

import java.util.List;

public interface CategoriaService {
    CategoriaResponseDTO crear(CategoriaRequestDTO categoriaRequestDTO);

    CategoriaResponseDTO actualizar(Long id, CategoriaRequestDTO categoriaRequestDTO);

    void eliminar(Long id);

    CategoriaResponseDTO obtenerPorId(Long id);

    List<CategoriaResponseDTO> obtenerTodos();
}
