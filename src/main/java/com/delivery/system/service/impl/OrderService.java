package com.delivery.system.service.impl;



import com.delivery.system.dto.OrderDTO;
import com.delivery.system.entity.Order;
import com.delivery.system.enums.OrderStatus;
import com.delivery.system.event.OrderCreatedEvent;
import com.delivery.system.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * =====================================================================
 * ORDER SERVICE
 * =====================================================================
 *
 * Handles all business logic related to orders:
 * - Create an order (save to DB + fire Kafka event)
 * - Cancel an order
 * - Get order status
 * - Retry failed orders (scheduled job)
 *
 * IMPORTANT: OrderService does NOT do dispatch.
 * It just creates the order and fires an event.
 * DispatchService (listening on Kafka) does the assignment.
 * → This is the SEPARATION OF CONCERNS principle.
 *
 * WHY @Transactional?
 * The @Transactional annotation wraps the method in a DB transaction.
 * If anything fails mid-way → entire operation is ROLLED BACK.
 * Example: We save order to DB, then Kafka send fails.
 * Without @Transactional → order is in DB but no event fired → inconsistency!
 * With @Transactional → both succeed or both fail together.
 *
 * (Note: Kafka send is NOT rolled back by DB transaction — for 100% consistency
 * you'd need the Outbox Pattern. Mentioned below.)
 *
 * =====================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Value("${kafka.topic.order-created}")
    private String orderCreatedTopic;

    // =========================================================
    // CREATE ORDER
    // =========================================================

    /**
     * Create a new order and trigger dispatch.
     *
     * STEP BY STEP:
     * 1. Build Order entity from request DTO
     * 2. Save to DB (status = PENDING)
     * 3. Publish ORDER_CREATED event to Kafka
     * 4. DispatchService picks up the event and assigns a driver
     * 5. Return order response to user (driver not yet assigned)
     *
     * @param request The order details from the user
     * @param userId The authenticated user's ID (from JWT token)
     * @return OrderResponse with order details (status = PENDING for now)
     */
    @Transactional
    public OrderDTO.OrderResponse createOrder(OrderDTO.CreateOrderRequest request, Long userId) {
        log.info("📝 Creating order for userId={}, restaurantId={}",
                userId, request.getRestaurantId());

        // STEP 1: Build the Order entity
        Order order = Order.builder()
                .userId(userId)
                .restaurantId(request.getRestaurantId())
                .status(OrderStatus.PENDING)
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .deliveryLatitude(request.getDeliveryLatitude())
                .deliveryLongitude(request.getDeliveryLongitude())
                .totalAmount(request.getTotalAmount())
                .specialInstructions(request.getSpecialInstructions())
                .assignmentAttempts(0)
                .build();

        // STEP 2: Save to DB
        // After save(), order.getId() is populated (DB generated it)
        Order savedOrder = orderRepository.save(order);
        log.info("💾 Order saved with id={}", savedOrder.getId());

        // STEP 3: Publish event to Kafka
        // DispatchService is subscribed and will pick this up
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .userId(userId)
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .eventTimestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(orderCreatedTopic, String.valueOf(savedOrder.getId()), event);
        log.info("📤 Published ORDER_CREATED event for orderId={}", savedOrder.getId());

        /*
         * ⚠️ INTERVIEW GOLD: THE OUTBOX PATTERN
         * ==========================================
         * Problem: What if app crashes BETWEEN saving to DB and sending to Kafka?
         * → Order is in DB but no event fired → order stuck PENDING forever!
         *
         * Solution: OUTBOX PATTERN
         * 1. Save order + outbox record in SAME DB transaction (atomic)
         * 2. A separate "outbox poller" reads unprocessed outbox records
         * 3. Poller publishes them to Kafka and marks as processed
         *
         * This guarantees "at-least-once" delivery to Kafka.
         * (Worth mentioning in interviews to show you think about edge cases)
         */

        return toOrderResponse(savedOrder);
    }

    // =========================================================
    // GET ORDER
    // =========================================================

    public OrderDTO.OrderResponse getOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Security: Users can only see their own orders
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied to order: " + orderId);
        }

        return toOrderResponse(order);
    }

    public List<OrderDTO.OrderResponse> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId)
                .stream()
                .map(this::toOrderResponse)
                .toList();
    }

    // =========================================================
    // CANCEL ORDER
    // =========================================================

    @Transactional
    public OrderDTO.OrderResponse cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        // Can only cancel if not yet picked up
        if (order.getStatus() == OrderStatus.PICKED_UP || order.getStatus() == OrderStatus.DELIVERED) {
            throw new RuntimeException("Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        // If driver was assigned, free them up
        if (saved.getDriver() != null) {
            saved.getDriver().setStatus(com.delivery.system.enums.DriverStatus.AVAILABLE);
        }

        log.info("🚫 Order {} cancelled by userId={}", orderId, userId);
        return toOrderResponse(saved);
    }

    // =========================================================
    // RETRY SCHEDULER
    // =========================================================

    /**
     * Periodically retry PENDING orders that failed to get a driver.
     *
     * @Scheduled → Spring runs this method every 30 seconds automatically.
     * No external cron job needed!
     *
     * HOW IT WORKS:
     * 1. Find all PENDING orders with < MAX_RETRIES attempts
     * 2. Re-publish ORDER_CREATED event for each
     * 3. DispatchService tries again
     *
     * WHY SEPARATE FROM KAFKA RETRY?
     * Kafka retries handle transport failures (Kafka unavailable).
     * This handles business failures (no drivers available at that moment).
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000) // every 30 seconds
    @Transactional
    public void retryPendingOrders() {
        List<Order> pendingOrders = orderRepository.findPendingOrdersForRetry(3);

        if (!pendingOrders.isEmpty()) {
            log.info("🔄 Retrying {} pending orders", pendingOrders.size());
        }

        for (Order order : pendingOrders) {
            OrderCreatedEvent retryEvent = OrderCreatedEvent.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .pickupLatitude(order.getPickupLatitude())
                    .pickupLongitude(order.getPickupLongitude())
                    .eventTimestamp(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(orderCreatedTopic, String.valueOf(order.getId()), retryEvent);
            log.debug("🔁 Retry event sent for order {}", order.getId());
        }
    }

    // =========================================================
    // MAPPER: Entity → DTO
    // =========================================================

    private OrderDTO.OrderResponse toOrderResponse(Order order) {
        var responseBuilder = OrderDTO.OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .restaurantId(order.getRestaurantId())
                .pickupLatitude(order.getPickupLatitude())
                .pickupLongitude(order.getPickupLongitude())
                .deliveryLatitude(order.getDeliveryLatitude())
                .deliveryLongitude(order.getDeliveryLongitude())
                .totalAmount(order.getTotalAmount())
                .specialInstructions(order.getSpecialInstructions())
                .assignmentAttempts(order.getAssignmentAttempts());

        if (order.getCreatedAt() != null) {
            responseBuilder.createdAt(order.getCreatedAt().toString());
        }

        // Include driver info if assigned
        if (order.getDriver() != null) {
            var driverInfo = OrderDTO.OrderResponse.DriverInfo.builder()
                    .driverId(order.getDriver().getId())
                    .driverName(order.getDriver().getName())
                    .driverPhone(order.getDriver().getPhoneNumber())
                    .driverRating(order.getDriver().getRating())
                    .build();
            responseBuilder.assignedDriver(driverInfo);
        }

        return responseBuilder.build();
    }
}