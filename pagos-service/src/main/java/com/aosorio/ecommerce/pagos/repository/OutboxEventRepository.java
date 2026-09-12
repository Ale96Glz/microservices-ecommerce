package com.aosorio.ecommerce.pagos.repository;

import com.aosorio.ecommerce.pagos.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByEstado(OutboxEvent.EstadoOutbox estado);
}