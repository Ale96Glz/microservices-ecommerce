package com.aosorio.ecommerce.pedidos.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import com.aosorio.ecommerce.pedidos.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pedidos.kafka.enabled", havingValue = "true")
public class PaymentProcessedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessedListener.class);

    private final PedidoService pedidoService;

    public PaymentProcessedListener(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_PROCESSED, groupId = "pedidos-service")
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        log.info("Recibido PaymentProcessedEvent para pedido {} con estado {}", event.pedidoId(), event.estado());
        pedidoService.procesarResultadoPago(event);
    }
}