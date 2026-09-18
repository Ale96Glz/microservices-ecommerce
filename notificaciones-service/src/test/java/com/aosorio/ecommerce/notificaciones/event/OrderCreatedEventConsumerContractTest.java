package com.aosorio.ecommerce.notificaciones.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import com.aosorio.ecommerce.events.OrderCreatedEvent;
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

class OrderCreatedEventConsumerContractTest {

    public static final OrderCreatedEvent EVENTO = new OrderCreatedEvent(
            1L, 2L, new BigDecimal("250.50"), Instant.parse("2026-09-18T10:00:00Z"));

    private static final String CONTRATO = "contracts/order-created-event.json";

    @Test
    void deserializaElContratoDocumentadoDelProductor() throws Exception {
        byte[] payload = new ClassPathResource(CONTRATO).getInputStream().readAllBytes();

        assertThat(deserializador().deserialize(KafkaTopics.ORDER_CREATED, payload)).isEqualTo(EVENTO);
    }

    @Test
    void deserializaElWireDelProductorDePedidos() {
        JsonSerializer<OrderCreatedEvent> serializador = new JsonSerializer<>();
        serializador.setAddTypeInfo(false);
        byte[] wire = serializador.serialize(KafkaTopics.ORDER_CREATED, EVENTO);

        assertThat(deserializador().deserialize(KafkaTopics.ORDER_CREATED, wire)).isEqualTo(EVENTO);
    }

    @Test
    void elTopicoConsumidoCoincideConLaConstanteComun() throws Exception {
        var metodo = OrderCreatedListener.class.getMethod("onOrderCreated", OrderCreatedEvent.class);
        assertThat(metodo.getAnnotation(KafkaListener.class).topics())
                .containsExactly(KafkaTopics.ORDER_CREATED);
    }

    private static JsonDeserializer<OrderCreatedEvent> deserializador() {
        Map<String, Object> props = new HashMap<>();
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aosorio.ecommerce.events");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderCreatedEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        JsonDeserializer<OrderCreatedEvent> deserializador = new JsonDeserializer<>();
        deserializador.configure(props, false);
        return deserializador;
    }
}