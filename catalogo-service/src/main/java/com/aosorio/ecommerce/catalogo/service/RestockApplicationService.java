package com.aosorio.ecommerce.catalogo.service;

import com.aosorio.ecommerce.catalogo.domain.RestockEvent;
import com.aosorio.ecommerce.catalogo.repository.RestockEventRepository;
import com.aosorio.ecommerce.events.RestockRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestockApplicationService {

    private final RestockEventRepository restockEventRepository;
    private final ProductoService productoService;

    @Transactional
    public void aplicar(RestockRequestedEvent event) {
        if (restockEventRepository.existsById(event.eventId())) {
            log.info("Restock {} del pedido {} ya aplicado. Se ignora por idempotencia.",
                    event.eventId(), event.pedidoId());
            return;
        }

        productoService.reponerStock(event.productoId(), event.cantidad());
        restockEventRepository.save(RestockEvent.builder()
                .eventId(event.eventId())
                .pedidoId(event.pedidoId())
                .productoId(event.productoId())
                .cantidad(event.cantidad())
                .build());
        log.info("Restock {} aplicado: {} unidades del producto {} para el pedido {}",
                event.eventId(), event.cantidad(), event.productoId(), event.pedidoId());
    }
}