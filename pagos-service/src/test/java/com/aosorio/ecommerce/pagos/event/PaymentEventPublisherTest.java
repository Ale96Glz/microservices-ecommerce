package com.aosorio.ecommerce.pagos.event;

import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentEventPublisherTest {

    private static final PaymentProcessedEvent EVENTO = new PaymentProcessedEvent(
            4L, 18L, 1L, new BigDecimal("120.00"), "RECHAZADO", "MONTO_MAXIMO", 1,
            Instant.parse("2026-09-20T06:24:00Z"));

    @Test
    void esperaLaConfirmacionDelBrokerAntesDeDarPorPublicado() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, PaymentProcessedEvent> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, PaymentProcessedEvent>> ok = new CompletableFuture<>();
        ok.complete(mock(SendResult.class));
        when(template.send(eq(PaymentEventPublisher.TOPIC), eq("4"), eq(EVENTO))).thenReturn(ok);

        publisher(true, template).publish(EVENTO);

        verify(template).send(PaymentEventPublisher.TOPIC, "4", EVENTO);
    }

    @Test
    void propagaElFalloSiKafkaNoConfirma() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, PaymentProcessedEvent> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, PaymentProcessedEvent>> fallo = new CompletableFuture<>();
        fallo.completeExceptionally(new RuntimeException("UNKNOWN_TOPIC_OR_PARTITION"));
        when(template.send(eq(PaymentEventPublisher.TOPIC), eq("4"), eq(EVENTO))).thenReturn(fallo);

        assertThatThrownBy(() -> publisher(true, template).publish(EVENTO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pago 4");
    }

    private static PaymentEventPublisher publisher(
            boolean enabled,
            KafkaTemplate<String, PaymentProcessedEvent> template
    ) {
        @SuppressWarnings("unchecked")
        ObjectProvider<KafkaTemplate<String, PaymentProcessedEvent>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return new PaymentEventPublisher(enabled, provider);
    }
}
