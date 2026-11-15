package com.aosorio.ecommerce.pagos.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void montoNoCoincideSeTraduceEn422() {
        String mensaje = "El monto 121 no coincide con el total del pedido 120.00";

        ResponseEntity<Map<String, Object>> response =
                handler.handleMontoNoCoincide(new MontoNoCoincideException(mensaje));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody())
                .containsEntry("status", 422)
                .containsEntry("message", mensaje);
    }
}
