package com.aosorio.ecommerce.pedidos.event;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.pedidos.domain.OutboxEvent;
import com.aosorio.ecommerce.pedidos.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${pedidos.outbox.poll-interval-ms:5000}")
    public void publicarPendientes() {
        List<OutboxEvent> pendientes = outboxEventRepository.findByEstado(OutboxEvent.EstadoOutbox.PENDIENTE);
        if (pendientes.isEmpty()) {
            return;
        }

        for (OutboxEvent outbox : pendientes) {
            try {
                OrderCreatedEvent event = objectMapper.readValue(outbox.getPayload(), OrderCreatedEvent.class);
                orderEventPublisher.publish(event);

                outbox.setEstado(OutboxEvent.EstadoOutbox.PUBLICADO);
                outbox.setFechaPublicacion(LocalDateTime.now());
                outboxEventRepository.save(outbox);
                log.info("Evento outbox {} publicado para pedido {}", outbox.getId(), event.pedidoId());
            } catch (Exception e) {
                log.error("No se pudo publicar el evento outbox {}", outbox.getId(), e);
            }
        }
    }
}