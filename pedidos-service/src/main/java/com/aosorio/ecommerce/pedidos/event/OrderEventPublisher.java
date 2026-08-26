package com.aosorio.ecommerce.pedidos.event;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    public static final String TOPIC = "order-created";
    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final boolean kafkaEnabled;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderEventPublisher(
            @Value("${pedidos.kafka.enabled:false}") boolean kafkaEnabled,
            ObjectProvider<KafkaTemplate<String, OrderCreatedEvent>> kafkaTemplate
    ) {
        this.kafkaEnabled = kafkaEnabled;
        this.kafkaTemplate = kafkaTemplate.getIfAvailable();
    }

    public void publish(OrderCreatedEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            log.info("Kafka deshabilitado. Evento OrderCreated del pedido {} no se publica.", event.pedidoId());
            return;
        }

        kafkaTemplate.send(TOPIC, event.pedidoId().toString(), event);
        log.info("Evento OrderCreated publicado para pedido {}", event.pedidoId());
    }
}
