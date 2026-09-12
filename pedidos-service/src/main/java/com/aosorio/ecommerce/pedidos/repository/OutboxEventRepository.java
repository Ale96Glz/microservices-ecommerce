package com.aosorio.ecommerce.pedidos.repository;

import com.aosorio.ecommerce.pedidos.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByEstado(OutboxEvent.EstadoOutbox estado);
}