package com.aosorio.ecommerce.catalogo.service;

import com.aosorio.ecommerce.catalogo.domain.Categoria;
import com.aosorio.ecommerce.catalogo.domain.Producto;
import com.aosorio.ecommerce.catalogo.dto.PageResponseDTO;
import com.aosorio.ecommerce.catalogo.dto.ProductoRequestDTO;
import com.aosorio.ecommerce.catalogo.dto.ProductoResponseDTO;
import com.aosorio.ecommerce.catalogo.exception.ResourceInUseException;
import com.aosorio.ecommerce.catalogo.exception.ResourceNotFoundException;
import com.aosorio.ecommerce.catalogo.mapper.ProductoMapper;
import com.aosorio.ecommerce.catalogo.repository.CategoriaRepository;
import com.aosorio.ecommerce.catalogo.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
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
    @Transactional
    public ProductoResponseDTO actualizar(Long id, ProductoRequestDTO productoRequestDTO) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con id: " + id));

        Categoria categoria = categoriaRepository.findById(productoRequestDTO.getCategoriaId())
                .orElseThrow(() -> new RuntimeException(
                        "No existe la categoria con id: " + productoRequestDTO.getCategoriaId()));

        producto.setNombre(productoRequestDTO.getNombre());
        producto.setDescripcion(productoRequestDTO.getDescripcion());
        producto.setPrecio(productoRequestDTO.getPrecio());
        producto.setStock(productoRequestDTO.getStock());
        producto.setCategoria(categoria);
        producto.setEstado(producto.getStock() > 0
                ? Producto.EstadoProducto.ACTIVO
                : Producto.EstadoProducto.AGOTADO);

        Producto actualizado = productoRepository.save(producto);
        log.info("Se ha actualizado el producto: {}", actualizado.getId());

        return productoMapper.toResponseDto(actualizado);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con id: " + id));
        productoRepository.delete(producto);
        log.info("Se ha eliminado el producto: {}", producto.getId());
    }

    @Override
    @Transactional
    public ProductoResponseDTO descontarStock(Long id, int cantidad) {
        log.info("Descontando {} unidades de stock del producto: {}", cantidad, id);

        int actualizados = productoRepository.descontarStock(id, cantidad);
        if (actualizados == 0) {
            Producto actual = productoRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontró el producto con id: " + id));
            throw new ResourceInUseException(
                    "Stock insuficiente para el producto " + id + ". Disponible: " + actual.getStock()
                            + ", solicitado: " + cantidad);
        }

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el producto con id: " + id));

        if (producto.getStock() <= 0) {
            producto.setEstado(Producto.EstadoProducto.AGOTADO);
        } else if (producto.getEstado() == Producto.EstadoProducto.AGOTADO) {
            producto.setEstado(Producto.EstadoProducto.ACTIVO);
        }

        Producto actualizado = productoRepository.save(producto);
        log.info("Stock actualizado del producto {}: {} unidades", id, actualizado.getStock());
        return productoMapper.toResponseDto(actualizado);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoResponseDTO obtenerPorId(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el producto con id: " + id));
        log.info("Se ha obtenido el producto: {}", producto.getId());
        return productoMapper.toResponseDto(producto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerTodos() {
        List<Producto> productos = productoRepository.findAll();
        log.info("Se han obtenido {} productos", productos.size());
        return productos.stream()
                .map(productoMapper::toResponseDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ProductoResponseDTO> obtenerTodosPaginado(Pageable pageable) {
        return PageResponseDTO.from(
                productoRepository.findAll(pageable).map(productoMapper::toResponseDto)
        );
    }
}
