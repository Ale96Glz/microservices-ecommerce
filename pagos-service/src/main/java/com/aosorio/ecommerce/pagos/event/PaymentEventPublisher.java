package com.aosorio.ecommerce.pagos.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import com.aosorio.ecommerce.events.PaymentProcessedEvent;
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
public class PaymentEventPublisher {

    public static final String TOPIC = KafkaTopics.PAYMENT_PROCESSED;
    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final boolean kafkaEnabled;
    private final KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate;

    public PaymentEventPublisher(
            @Value("${pagos.kafka.enabled:false}") boolean kafkaEnabled,
            ObjectProvider<KafkaTemplate<String, PaymentProcessedEvent>> kafkaTemplate
    ) {
        this.kafkaEnabled = kafkaEnabled;
        this.kafkaTemplate = kafkaTemplate.getIfAvailable();
    }

    public void publish(PaymentProcessedEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            log.info("Kafka deshabilitado. Evento PaymentProcessed del pago {} no se publica.", event.pagoId());
            return;
        }

        try {
            kafkaTemplate.send(TOPIC, event.pagoId().toString(), event).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envío a Kafka de PaymentProcessed interrumpido", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(
                    "Kafka no confirmó PaymentProcessed del pago " + event.pagoId(), e);
        }
        log.info("Evento PaymentProcessed publicado para pago {}", event.pagoId());
    }
}
