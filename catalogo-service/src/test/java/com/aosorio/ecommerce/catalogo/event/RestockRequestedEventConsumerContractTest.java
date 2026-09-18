package com.aosorio.ecommerce.catalogo.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import com.aosorio.ecommerce.events.RestockRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestockRequestedEventConsumerContractTest {

    public static final RestockRequestedEvent EVENTO = new RestockRequestedEvent(
            "5-3", 5L, 3L, 2, Instant.parse("2026-09-18T10:05:00Z"));

    private static final String CONTRATO = "contracts/restock-requested-event.json";

    @Test
    void deserializaElContratoDocumentadoDelProductor() throws Exception {
        byte[] payload = new ClassPathResource(CONTRATO).getInputStream().readAllBytes();

        assertThat(deserializador().deserialize(KafkaTopics.RESTOCK_REQUESTED, payload)).isEqualTo(EVENTO);
    }

    @Test
    void deserializaElWireDelProductorDePedidos() {
        JsonSerializer<RestockRequestedEvent> serializador = new JsonSerializer<>();
        serializador.setAddTypeInfo(false);
        byte[] wire = serializador.serialize(KafkaTopics.RESTOCK_REQUESTED, EVENTO);

        assertThat(deserializador().deserialize(KafkaTopics.RESTOCK_REQUESTED, wire)).isEqualTo(EVENTO);
    }

    @Test
    void elTopicoConsumidoCoincideConLaConstanteComun() throws Exception {
        var metodo = RestockRequestedListener.class.getMethod("onRestockRequested", RestockRequestedEvent.class);
        assertThat(metodo.getAnnotation(KafkaListener.class).topics())
                .containsExactly(KafkaTopics.RESTOCK_REQUESTED);
    }

    private static JsonDeserializer<RestockRequestedEvent> deserializador() {
        Map<String, Object> props = new HashMap<>();
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aosorio.ecommerce.events");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, RestockRequestedEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        JsonDeserializer<RestockRequestedEvent> deserializador = new JsonDeserializer<>();
        deserializador.configure(props, false);
        return deserializador;
    }
}