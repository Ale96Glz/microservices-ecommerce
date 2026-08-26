package com.aosorio.ecommerce.catalogo.mapper;

import com.aosorio.ecommerce.catalogo.domain.Categoria;
import com.aosorio.ecommerce.catalogo.domain.Producto;
import com.aosorio.ecommerce.catalogo.dto.ProductoRequestDTO;
import com.aosorio.ecommerce.catalogo.dto.ProductoResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ProductoMapper {

    public Producto toEntity(ProductoRequestDTO dto, Categoria categoria) {
        return Producto.builder()
                .nombre(dto.getNombre())
                .descripcion(dto.getDescripcion())
                .precio(dto.getPrecio())
                .stock(dto.getStock())
                .categoria(categoria)
                .estado(Producto.EstadoProducto.ACTIVO)
                .build();
    }

    public ProductoResponseDTO toResponseDto(Producto producto) {
        String estado = producto.getEstado() != null ? producto.getEstado().name() : null;
        String categoria = producto.getCategoria() != null ? producto.getCategoria().getNombre() : null;

        return new ProductoResponseDTO(
                producto.getId(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getPrecio(),
                producto.getStock(),
                estado,
                categoria,
                producto.getFechaCreacion()
        );
    }
}
