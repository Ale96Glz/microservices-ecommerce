package com.aosorio.ecommerce.pedidos.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;

class HttpClientConfigTest {

    @Test
    void customizeAplicaSinError() {
        RestClient.Builder builder = RestClient.builder();
        assertThatCode(() -> new HttpClientConfig()
                .restClientTimeouts(Duration.ofSeconds(2), Duration.ofSeconds(5))
                .customize(builder))
                .doesNotThrowAnyException();
        builder.build();
    }
}
