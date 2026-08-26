package com.aosorio.ecommerce.pagos.repository;

import com.aosorio.ecommerce.pagos.domain.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    Optional<Pago> findByPedidoId(Long pedidoId);

    boolean existsByPedidoId(Long pedidoId);

    List<Pago> findByUsuarioId(Long usuarioId);
}
