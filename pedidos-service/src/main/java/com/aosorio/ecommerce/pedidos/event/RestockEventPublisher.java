package com.aosorio.ecommerce.pedidos.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import com.aosorio.ecommerce.events.RestockRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class RestockEventPublisher {

    public static final String TOPIC = KafkaTopics.RESTOCK_REQUESTED;
    private static final Logger log = LoggerFactory.getLogger(RestockEventPublisher.class);

    private final boolean kafkaEnabled;
    private final KafkaTemplate<String, RestockRequestedEvent> kafkaTemplate;

    public RestockEventPublisher(
            @Value("${pedidos.kafka.enabled:false}") boolean kafkaEnabled,
            ObjectProvider<KafkaTemplate<String, RestockRequestedEvent>> kafkaTemplate
    ) {
        this.kafkaEnabled = kafkaEnabled;
        this.kafkaTemplate = kafkaTemplate.getIfAvailable();
    }

    public void publish(RestockRequestedEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            log.info("Kafka deshabilitado. Evento RestockRequested del pedido {} no se publica.",
                    event.pedidoId());
            return;
        }

        try {
            kafkaTemplate.send(TOPIC, event.eventId(), event).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envío a Kafka de RestockRequested interrumpido", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(
                    "Kafka no confirmó RestockRequested " + event.eventId() + " del pedido " + event.pedidoId(), e);
        }
        log.info("Evento RestockRequested {} publicado para pedido {}", event.eventId(), event.pedidoId());
    }
}