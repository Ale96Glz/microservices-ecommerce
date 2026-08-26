package com.aosorio.ecommerce.catalogo.service;

import com.aosorio.ecommerce.catalogo.dto.PageResponseDTO;
import com.aosorio.ecommerce.catalogo.dto.ProductoRequestDTO;
import com.aosorio.ecommerce.catalogo.dto.ProductoResponseDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductoService {
    ProductoResponseDTO crear(ProductoRequestDTO productoRequestDTO);

    ProductoResponseDTO actualizar(Long id, ProductoRequestDTO productoRequestDTO);

    void eliminar(Long id);

    ProductoResponseDTO obtenerPorId(Long id);

    List<ProductoResponseDTO> obtenerTodos();

    PageResponseDTO<ProductoResponseDTO> obtenerTodosPaginado(Pageable pageable);
}
