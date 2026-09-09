package com.aosorio.ecommerce.notificaciones.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notificaciones.kafka.enabled", havingValue = "true")
public class DltListener {

    private static final Logger log = LoggerFactory.getLogger(DltListener.class);

    @KafkaListener(
            topics = {KafkaTopics.ORDER_CREATED + ".DLT", KafkaTopics.PAYMENT_PROCESSED + ".DLT"},
            groupId = "notificaciones-service-dlt",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void onDlt(String payload) {
        log.error("Evento enviado a DLT tras agotar reintentos: {}", payload);
    }
}