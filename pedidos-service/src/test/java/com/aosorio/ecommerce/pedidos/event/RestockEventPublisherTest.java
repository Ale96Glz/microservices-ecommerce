package com.aosorio.ecommerce.pedidos.event;

import com.aosorio.ecommerce.events.RestockRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestockEventPublisherTest {

    private static final RestockRequestedEvent EVENTO =
            new RestockRequestedEvent("18-4-intento1", 18L, 4L, 4, Instant.parse("2026-09-20T06:24:00Z"));

    @Test
    void esperaLaConfirmacionDelBrokerAntesDeDarPorPublicado() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, RestockRequestedEvent> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, RestockRequestedEvent>> ok = new CompletableFuture<>();
        ok.complete(mock(SendResult.class));
        when(template.send(eq(RestockEventPublisher.TOPIC), eq(EVENTO.eventId()), eq(EVENTO))).thenReturn(ok);

        publisher(true, template).publish(EVENTO);

        verify(template).send(RestockEventPublisher.TOPIC, EVENTO.eventId(), EVENTO);
    }

    @Test
    void propagaElFalloSiKafkaNoConfirma() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, RestockRequestedEvent> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, RestockRequestedEvent>> fallo = new CompletableFuture<>();
        fallo.completeExceptionally(new RuntimeException("UNKNOWN_TOPIC_OR_PARTITION"));
        when(template.send(eq(RestockEventPublisher.TOPIC), eq(EVENTO.eventId()), eq(EVENTO))).thenReturn(fallo);

        assertThatThrownBy(() -> publisher(true, template).publish(EVENTO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("18-4-intento1");
    }

    private static RestockEventPublisher publisher(
            boolean enabled,
            KafkaTemplate<String, RestockRequestedEvent> template
    ) {
        @SuppressWarnings("unchecked")
        ObjectProvider<KafkaTemplate<String, RestockRequestedEvent>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return new RestockEventPublisher(enabled, provider);
    }
}
