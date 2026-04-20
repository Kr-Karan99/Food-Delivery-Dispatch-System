package com.delivery.system.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * =====================================================================
 * KAFKA CONFIGURATION
 * =====================================================================
 *
 * WHAT IS KAFKA?
 * A distributed message streaming platform. Think of it as a
 * super-powered, durable, scalable "message pipe".
 *
 * KEY CONCEPTS:
 *
 * 📬 TOPIC: Named channel for a type of message.
 *    - "order.created" topic → only ORDER_CREATED events flow here
 *    - Like separate email inboxes for different purposes
 *
 * 📤 PRODUCER: Sends messages to a topic.
 *    - OrderService is the producer for "order.created"
 *
 * 📥 CONSUMER: Reads messages from a topic.
 *    - DispatchService is the consumer of "order.created"
 *
 * 👥 CONSUMER GROUP:
 *    - Multiple DispatchService instances share a group ID
 *    - Kafka ensures each message is processed by ONLY ONE instance
 *    - This gives us automatic load balancing!
 *
 * 🗂️ PARTITION:
 *    - Topics are split into partitions for parallelism
 *    - 3 partitions → 3 consumers can process simultaneously
 *    - Messages in same partition are ordered
 *
 * 🔁 REPLICATION FACTOR:
 *    - How many Kafka brokers hold a copy of the data
 *    - replicas=1 for development (production: replicas=3 minimum)
 *    - If a broker dies → other replicas take over
 *
 * WHY KAFKA AND NOT A DIRECT HTTP CALL?
 * ┌─────────────────────────────────────────────────────────┐
 * │ Direct HTTP Call (Bad for high load):                   │
 * │   OrderService → DispatchService (synchronous, blocking)│
 * │   If Dispatch is slow → Order request times out ❌      │
 * │                                                         │
 * │ Kafka (Event-Driven, Better):                           │
 * │   OrderService → Kafka (instant) ✅                     │
 * │   Dispatch reads Kafka at its own pace ✅               │
 * │   Order creation = always fast, never blocked ✅        │
 * └─────────────────────────────────────────────────────────┘
 *
 * =====================================================================
 */
@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.order-created}")
    private String orderCreatedTopic;

    @Value("${kafka.topic.driver-assigned}")
    private String driverAssignedTopic;

    @Value("${kafka.topic.order-cancelled}")
    private String orderCancelledTopic;

    /**
     * Define the ORDER_CREATED topic.
     *
     * partitions(3) → 3 parallel "lanes" for processing
     * replicas(1)   → 1 copy (for local dev; use 3 in production)
     *
     * Spring Boot auto-creates this topic when app starts
     * (if spring.kafka.admin.auto-create=true)
     */
    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder
                .name(orderCreatedTopic)
                .partitions(3)   // 3 dispatch workers can run in parallel
                .replicas(1)     // Local dev: 1. Production: 3
                .build();
    }

    @Bean
    public NewTopic driverAssignedTopic() {
        return TopicBuilder
                .name(driverAssignedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder
                .name(orderCancelledTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}