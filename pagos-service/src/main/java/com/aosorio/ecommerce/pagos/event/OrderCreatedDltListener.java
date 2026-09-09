package com.aosorio.ecommerce.pagos.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pagos.kafka.enabled", havingValue = "true")
public class OrderCreatedDltListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedDltListener.class);

    @KafkaListener(
            topics = KafkaTopics.ORDER_CREATED + ".DLT",
            groupId = "pagos-service-dlt",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void onOrderCreatedDlt(String payload) {
        log.error("Evento OrderCreated enviado a DLT tras agotar reintentos: {}", payload);
    }
}