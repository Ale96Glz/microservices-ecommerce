package com.aosorio.ecommerce.catalogo.service;

public interface CategoriaService {
    CategoriaResponseDTO crear(CategoriaRequestDTO categoriaRequestDTO);
    CategoriaResponseDTO actualizar(CategoriaRequestDTO categoriaRequestDTO);
    void eliminar(CategoriaRequestDTO categoriaRequestDTO);
    CategoriaResponseDTO obtenerPorId(Long id);
    List<CategoriaResponseDTO> obtenerTodos();
}