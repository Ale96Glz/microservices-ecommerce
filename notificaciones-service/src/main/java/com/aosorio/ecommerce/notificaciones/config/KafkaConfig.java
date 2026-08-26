package com.aosorio.ecommerce.notificaciones.config;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
@ConditionalOnProperty(name = "notificaciones.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        return new DefaultKafkaConsumerFactory<>(consumerProps(bootstrapServers, OrderCreatedEvent.class));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> orderCreatedKafkaListenerContainerFactory(
            ConsumerFactory<String, OrderCreatedEvent> orderCreatedConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCreatedConsumerFactory);
        return factory;
    }

    @Bean
    ConsumerFactory<String, PaymentProcessedEvent> paymentProcessedConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        return new DefaultKafkaConsumerFactory<>(consumerProps(bootstrapServers, PaymentProcessedEvent.class));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, PaymentProcessedEvent> paymentProcessedKafkaListenerContainerFactory(
            ConsumerFactory<String, PaymentProcessedEvent> paymentProcessedConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentProcessedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentProcessedConsumerFactory);
        return factory;
    }

    private Map<String, Object> consumerProps(String bootstrapServers, Class<?> defaultType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notificaciones-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aosorio.ecommerce.events");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, defaultType.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
    }
}
