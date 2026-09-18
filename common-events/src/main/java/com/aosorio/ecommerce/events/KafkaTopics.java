package com.aosorio.ecommerce.events;

public final class KafkaTopics {

    public static final String ORDER_CREATED = "order-created";
    public static final String PAYMENT_PROCESSED = "payment-processed";
    public static final String RESTOCK_REQUESTED = "restock-requested";

    private KafkaTopics() {
    }
}