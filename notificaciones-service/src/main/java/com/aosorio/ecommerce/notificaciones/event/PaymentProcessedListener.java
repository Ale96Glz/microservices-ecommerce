package com.aosorio.ecommerce.notificaciones.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import com.aosorio.ecommerce.notificaciones.service.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notificaciones.kafka.enabled", havingValue = "true")
public class PaymentProcessedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessedListener.class);

    private final NotificacionService notificacionService;

    public PaymentProcessedListener(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_PROCESSED,
            groupId = "notificaciones-service",
            containerFactory = "paymentProcessedKafkaListenerContainerFactory"
    )
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        log.info("Recibido PaymentProcessedEvent para pago {}", event.pagoId());
        notificacionService.registrarPagoProcesado(event);
    }
}
