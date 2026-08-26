package com.aosorio.ecommerce.notificaciones.event;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.notificaciones.service.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notificaciones.kafka.enabled", havingValue = "true")
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final NotificacionService notificacionService;

    public OrderCreatedListener(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @KafkaListener(
            topics = "order-created",
            groupId = "notificaciones-service",
            containerFactory = "orderCreatedKafkaListenerContainerFactory"
    )
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Recibido OrderCreatedEvent para pedido {}", event.pedidoId());
        notificacionService.registrarPedidoCreado(event);
    }
}
