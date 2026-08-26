package com.aosorio.ecommerce.pagos.event;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.pagos.service.PagoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pagos.kafka.enabled", havingValue = "true")
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final PagoService pagoService;

    public OrderCreatedListener(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @KafkaListener(topics = "order-created", groupId = "pagos-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Recibido OrderCreatedEvent para pedido {}", event.pedidoId());
        pagoService.procesarDesdeEvento(event);
    }
}
