package com.delivery.system.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * =====================================================================
 * KAFKA EVENT: OrderCreatedEvent
 * =====================================================================
 *
 * WHAT IS A KAFKA EVENT?
 * A simple Java object (POJO) that gets:
 *   1. Serialized to JSON by the producer (OrderService)
 *   2. Sent to a Kafka topic (like putting a message in a pipe)
 *   3. Deserialized from JSON by the consumer (DispatchService)
 *
 * THINK OF IT LIKE EMAIL:
 *   - OrderService = sender who writes the email
 *   - Kafka topic  = the email server that holds messages
 *   - DispatchService = receiver who reads and acts on it
 *
 * WHY NOT JUST CALL DispatchService DIRECTLY?
 * ❌ Direct call = tight coupling:
 *     OrderService --HTTP--> DispatchService
 *     If DispatchService is down → OrderService FAILS
 *
 * ✅ Event-driven = loose coupling:
 *     OrderService → Kafka ← DispatchService picks up when ready
 *     If DispatchService is down → Kafka HOLDS the message
 *     When it comes back up → processes all pending messages
 *
 * =====================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    /**
     * Which order was created?
     * DispatchService uses this to fetch full order details from DB.
     */
    private Long orderId;

    /**
     * User who placed the order.
     * Used to send WebSocket notification back to the user.
     */
    private Long userId;

    /**
     * WHERE should the driver go to pick up food?
     * Used by Redis GEO query to find nearby drivers.
     * (We search near pickup location, NOT delivery location)
     */
    private Double pickupLatitude;
    private Double pickupLongitude;

    /**
     * WHEN was this event created?
     * Useful for debugging: "How long did this event sit in Kafka?"
     */
    private LocalDateTime eventTimestamp;
}
