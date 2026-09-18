package com.aosorio.ecommerce.pedidos.event;

import com.aosorio.ecommerce.events.KafkaTopics;
import com.aosorio.ecommerce.events.OrderCreatedEvent;
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

class OrderCreatedEventContractTest {

    public static final OrderCreatedEvent EVENTO = new OrderCreatedEvent(
            1L, 2L, new BigDecimal("250.50"), Instant.parse("2026-09-18T10:00:00Z"));

    private static final String CONTRATO = "contracts/order-created-event.json";

    @Test
    void payloadDelOutboxCoincideConElContratoDocumentado() throws Exception {
        ObjectMapper boot = bootObjectMapper();
        byte[] payload = boot.writeValueAsBytes(EVENTO);

        JsonNode emitido = boot.readTree(payload);
        JsonNode contrato = boot.readTree(new ClassPathResource(CONTRATO).getInputStream());

        assertThat(emitido).isEqualTo(contrato);
        assertThat(boot.readValue(payload, OrderCreatedEvent.class)).isEqualTo(EVENTO);
    }

    @Test
    void wireDeKafkaConservaLasClavesYValoresDelContrato() throws Exception {
        JsonSerializer<OrderCreatedEvent> serializador = new JsonSerializer<>();
        serializador.setAddTypeInfo(false);
        byte[] wire = serializador.serialize(OrderEventPublisher.TOPIC, EVENTO);

        JsonNode emitido = new ObjectMapper().readTree(wire);
        JsonNode contrato = bootObjectMapper()
                .readTree(new ClassPathResource(CONTRATO).getInputStream());

        assertThat(claves(emitido)).isEqualTo(claves(contrato));
        assertThat(emitido.get("pedidoId").asLong()).isEqualTo(contrato.get("pedidoId").asLong());
        assertThat(emitido.get("usuarioId").asLong()).isEqualTo(contrato.get("usuarioId").asLong());
        assertThat(emitido.get("total").decimalValue())
                .isEqualByComparingTo(contrato.get("total").decimalValue());
        assertThat(instantDe(emitido.get("creadoEn"))).isEqualTo(instantDe(contrato.get("creadoEn")));
    }

    @Test
    void elWireDelProductorLoReconstruyeElReceptor() {
        JsonSerializer<OrderCreatedEvent> serializador = new JsonSerializer<>();
        serializador.setAddTypeInfo(false);
        byte[] wire = serializador.serialize(OrderEventPublisher.TOPIC, EVENTO);

        JsonDeserializer<OrderCreatedEvent> deserializador = new JsonDeserializer<>();
        deserializador.configure(propsReceptor(), false);

        assertThat(deserializador.deserialize(OrderEventPublisher.TOPIC, wire)).isEqualTo(EVENTO);
    }

    @Test
    void elTopicoDelProductorCoincideConLaConstanteComun() {
        assertThat(OrderEventPublisher.TOPIC).isEqualTo(KafkaTopics.ORDER_CREATED);
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
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderCreatedEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
    }

    private static ObjectMapper bootObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}