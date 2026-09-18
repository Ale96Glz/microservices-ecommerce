package com.aosorio.ecommerce.catalogo.event;

import com.aosorio.ecommerce.catalogo.service.RestockApplicationService;
import com.aosorio.ecommerce.events.KafkaTopics;
import com.aosorio.ecommerce.events.RestockRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestockRequestedListener {

    private final RestockApplicationService restockApplicationService;

    @KafkaListener(topics = KafkaTopics.RESTOCK_REQUESTED)
    public void onRestockRequested(RestockRequestedEvent event) {
        log.info("Solicitud de reposicion recibida: {}", event.eventId());
        restockApplicationService.aplicar(event);
    }
}