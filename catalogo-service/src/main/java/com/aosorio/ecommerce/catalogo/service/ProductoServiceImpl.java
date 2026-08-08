package com.aosorio.ecommerce.catalogo.service;

import com.aosorio.ecommerce.catalogo.domain.Categoria;
import com.aosorio.ecommerce.catalogo.domain.Producto;
import com.aosorio.ecommerce.catalogo.dto.ProductoRequestDTO;
import com.aosorio.ecommerce.catalogo.dto.ProductoResponseDTO;
import com.aosorio.ecommerce.catalogo.mapper.ProductoMapper;
import com.aosorio.ecommerce.catalogo.repository.CategoriaRepository;
import com.aosorio.ecommerce.catalogo.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductoServiceImpl implements ProductoService {
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoMapper productoMapper;

    @Override
    @Transactional
    public ProductoResponseDTO crear(ProductoRequestDTO productoRequestDTO) {
        log.info("Iniciando la creacion del producto: {}", productoRequestDTO.getNombre());

        if (productoRepository.existsByNombre(productoRequestDTO.getNombre())) {
            throw new RuntimeException("Ya existe un producto con el nombre: " + productoRequestDTO.getNombre());
        }

        Categoria categoria = categoriaRepository.findById(productoRequestDTO.getCategoriaId())
                .orElseThrow(() -> new RuntimeException(
                        "No existe la categoria con id: " + productoRequestDTO.getCategoriaId()));

        Producto producto = productoMapper.toEntity(productoRequestDTO, categoria);
        Producto guardado = productoRepository.save(producto);
        log.info("Se ha guardado el producto: {}", guardado.getId());

        return productoMapper.toResponseDto(guardado);
    }

    @Override
    public ProductoResponseDTO actualizar(ProductoRequestDTO productoRequestDTO) {
        return null;
    }

    @Override
    public void eliminar(ProductoRequestDTO productoRequestDTO) {

    }

    @Override
    public ProductoResponseDTO obtenerPorId(Long id) {
        return null;
    }

    @Override
    public List<ProductoResponseDTO> obtenerTodos() {
        return List.of();
    }
}
