package com.aosorio.ecommerce.pedidos.repository;

import com.aosorio.ecommerce.pedidos.domain.Pedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Override
    @EntityGraph(attributePaths = "items")
    List<Pedido> findAll();

    @Override
    @EntityGraph(attributePaths = "items")
    Page<Pedido> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<Pedido> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    List<Pedido> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = "items")
    Page<Pedido> findByUsuarioId(Long usuarioId, Pageable pageable);
}
