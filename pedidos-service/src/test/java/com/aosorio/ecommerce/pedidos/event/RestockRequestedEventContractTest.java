package com.aosorio.ecommerce.pedidos.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import com.aosorio.ecommerce.events.RestockRequestedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RestockRequestedEventContractTest {

    public static final RestockRequestedEvent EVENTO = new RestockRequestedEvent(
            "5-3", 5L, 3L, 2, Instant.parse("2026-09-18T10:05:00Z"));

    private static final String CONTRATO = "contracts/restock-requested-event.json";

    @Test
    void payloadDelOutboxCoincideConElContratoDocumentado() throws Exception {
        ObjectMapper boot = bootObjectMapper();
        byte[] payload = boot.writeValueAsBytes(EVENTO);

        JsonNode emitido = boot.readTree(payload);
        JsonNode contrato = boot.readTree(new ClassPathResource(CONTRATO).getInputStream());

        assertThat(emitido).isEqualTo(contrato);
        assertThat(boot.readValue(payload, RestockRequestedEvent.class)).isEqualTo(EVENTO);
    }

    @Test
    void wireDeKafkaConservaLasClavesYValoresDelContrato() throws Exception {
        JsonSerializer<RestockRequestedEvent> serializador = new JsonSerializer<>();
        serializador.setAddTypeInfo(false);
        byte[] wire = serializador.serialize(RestockEventPublisher.TOPIC, EVENTO);

        JsonNode emitido = new ObjectMapper().readTree(wire);
        JsonNode contrato = bootObjectMapper()
                .readTree(new ClassPathResource(CONTRATO).getInputStream());

        assertThat(claves(emitido)).isEqualTo(claves(contrato));
        assertThat(emitido.get("eventId").asText()).isEqualTo(contrato.get("eventId").asText());
        assertThat(emitido.get("pedidoId").asLong()).isEqualTo(contrato.get("pedidoId").asLong());
        assertThat(emitido.get("productoId").asLong()).isEqualTo(contrato.get("productoId").asLong());
        assertThat(emitido.get("cantidad").asInt()).isEqualTo(contrato.get("cantidad").asInt());
        assertThat(instantDe(emitido.get("solicitadoEn"))).isEqualTo(instantDe(contrato.get("solicitadoEn")));
    }

    @Test
    void elWireDelProductorLoReconstruyeElReceptor() {
        JsonSerializer<RestockRequestedEvent> serializador = new JsonSerializer<>();
        serializador.setAddTypeInfo(false);
        byte[] wire = serializador.serialize(RestockEventPublisher.TOPIC, EVENTO);

        JsonDeserializer<RestockRequestedEvent> deserializador = new JsonDeserializer<>();
        deserializador.configure(propsReceptor(), false);

        assertThat(deserializador.deserialize(RestockEventPublisher.TOPIC, wire)).isEqualTo(EVENTO);
    }

    @Test
    void elTopicoDelProductorCoincideConLaConstanteComun() {
        assertThat(RestockEventPublisher.TOPIC).isEqualTo(KafkaTopics.RESTOCK_REQUESTED);
    }

    private static Set<String> claves(JsonNode nodo) {
        Set<String> claves = new HashSet<>();
        nodo.fieldNames().forEachRemaining(claves::add);
        return claves;
    }

    private static Instant instantDe(JsonNode nodo) {
        if (nodo.isTextual()) {
            return Instant.parse(nodo.asText());
        }
        if (nodo.isNumber()) {
            BigDecimal valor = nodo.decimalValue();
            long seconds = valor.longValue();
            long nanos = valor.subtract(BigDecimal.valueOf(seconds))
                    .movePointRight(9).longValue();
            return Instant.ofEpochSecond(seconds, nanos);
        }
        throw new IllegalArgumentException("El campo de tiempo debe ser texto o numero");
    }

    private static Map<String, Object> propsReceptor() {
        Map<String, Object> props = new HashMap<>();
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aosorio.ecommerce.events");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, RestockRequestedEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
    }

    private static ObjectMapper bootObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}