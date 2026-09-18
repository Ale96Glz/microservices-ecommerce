package com.aosorio.ecommerce.notificaciones.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProcessedEventConsumerContractTest {

    public static final PaymentProcessedEvent EVENTO = new PaymentProcessedEvent(
            10L, 1L, 2L, new BigDecimal("250.50"), "PROCESADO", Instant.parse("2026-09-18T10:05:00Z"));

    private static final String CONTRATO = "contracts/payment-processed-event.json";

    @Test
    void deserializaElContratoDocumentadoDelProductor() throws Exception {
        byte[] payload = new ClassPathResource(CONTRATO).getInputStream().readAllBytes();

        assertThat(deserializador().deserialize(KafkaTopics.PAYMENT_PROCESSED, payload)).isEqualTo(EVENTO);
    }

    @Test
    void deserializaElWireDelProductorDePagos() {
        JsonSerializer<PaymentProcessedEvent> serializador = new JsonSerializer<>();
        serializador.setAddTypeInfo(false);
        byte[] wire = serializador.serialize(KafkaTopics.PAYMENT_PROCESSED, EVENTO);

        assertThat(deserializador().deserialize(KafkaTopics.PAYMENT_PROCESSED, wire)).isEqualTo(EVENTO);
    }

    @Test
    void elTopicoConsumidoCoincideConLaConstanteComun() throws Exception {
        var metodo = PaymentProcessedListener.class.getMethod(
                "onPaymentProcessed", PaymentProcessedEvent.class);
        assertThat(metodo.getAnnotation(KafkaListener.class).topics())
                .containsExactly(KafkaTopics.PAYMENT_PROCESSED);
    }

    private static JsonDeserializer<PaymentProcessedEvent> deserializador() {
        Map<String, Object> props = new HashMap<>();
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aosorio.ecommerce.events");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentProcessedEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        JsonDeserializer<PaymentProcessedEvent> deserializador = new JsonDeserializer<>();
        deserializador.configure(props, false);
        return deserializador;
    }
}