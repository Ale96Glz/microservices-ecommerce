package com.aosorio.ecommerce.catalogo.repository;

import com.aosorio.ecommerce.catalogo.domain.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    @Override
    @EntityGraph(attributePaths = "categoria")
    Page<Producto> findAll(Pageable pageable);

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    Optional<Producto> findByNombreAndEstado(String nombre, Producto.EstadoProducto estado);

    boolean existsByNombre(String nombre);

    boolean existsByCategoriaId(Long categoriaId);

    long countByCategoriaId(Long categoriaId);

    @EntityGraph(attributePaths = "categoria")
    Page<Producto> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE Producto p
               SET p.stock = p.stock - :cantidad
             WHERE p.id = :id
               AND p.stock >= :cantidad
            """)
    int descontarStock(@Param("id") Long id, @Param("cantidad") int cantidad);
}
