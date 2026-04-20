package com.delivery.system.service.impl;


import com.delivery.system.entity.Driver;
import com.delivery.system.entity.Order;
import com.delivery.system.enums.DriverStatus;
import com.delivery.system.enums.OrderStatus;
import com.delivery.system.event.DriverAssignedEvent;
import com.delivery.system.event.OrderCreatedEvent;
import com.delivery.system.repository.DriverRepository;
import com.delivery.system.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * =====================================================================
 * 🧠 DISPATCH SERVICE — THE HEART OF THE SYSTEM
 * =====================================================================
 *
 * This is THE most important class. It answers one question:
 * "Which driver should deliver this order?"
 *
 * FLOW:
 * 1. Kafka delivers an ORDER_CREATED event to us
 * 2. We query Redis GEO for nearby available drivers
 * 3. We try to lock the best driver (Redis distributed lock)
 * 4. If locked → assign driver, update DB, notify via WebSocket + Kafka
 * 5. If lock fails → try next driver
 * 6. If no drivers → retry later
 *
 * CONCURRENCY PROBLEM WE'RE SOLVING:
 * Imagine 1000 orders arrive per second.
 * Each order's dispatch runs in a separate thread/instance.
 * Without locking → 100 orders could grab driver "Raju" simultaneously!
 * With Redis lock → only 1 order gets Raju; others move to next driver.
 *
 * =====================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j  // Adds "log" variable for logging (from Lombok)
public class DispatchService {

    // =========================================================
    // DEPENDENCIES (injected via @RequiredArgsConstructor)
    // =========================================================

    private final OrderRepository orderRepository;
    private final DriverRepository driverRepository;

    /**
     * KafkaTemplate: Our tool to SEND events to Kafka topics.
     * Like a "Kafka publisher" — we call kafkaTemplate.send(topic, event)
     */
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * RedisTemplate: Our tool to run Redis commands.
     * GEOADD, GEORADIUS, SET NX PX (for locks) etc.
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * SimpMessagingTemplate: Sends WebSocket messages to connected clients.
     * messagingTemplate.convertAndSend("/topic/orders/123", payload)
     * → All clients subscribed to that channel receive it instantly
     */
    private final SimpMessagingTemplate messagingTemplate;

    // =========================================================
    // CONSTANTS (from application.properties)
    // =========================================================

    @Value("${redis.key.driver-location}")
    private String driverLocationKey;  // "driver:locations"

    @Value("${redis.key.driver-lock-prefix}")
    private String driverLockPrefix;   // "driver:lock:"

    @Value("${redis.lock.ttl-seconds}")
    private long lockTtlSeconds;       // 10 seconds

    @Value("${kafka.topic.driver-assigned}")
    private String driverAssignedTopic;

    private static final int MAX_ASSIGNMENT_ATTEMPTS = 3;
    private static final double SEARCH_RADIUS_KM = 5.0;

    // =========================================================
    // STEP 1: CONSUME ORDER_CREATED EVENT FROM KAFKA
    // =========================================================

    /**
     * @KafkaListener:
     * Spring automatically calls this method when a new message
     * arrives in the "order.created" Kafka topic.
     *
     * Think of it as: "Hey DispatchService, a new order just arrived!"
     *
     * groupId = "dispatch-group":
     * If we have 3 DispatchService instances running, Kafka
     * ensures only ONE of them processes each order. Load balanced!
     */
    @KafkaListener(
            topics = "${kafka.topic.order-created}",
            groupId = "dispatch-group"
    )
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("📦 Received ORDER_CREATED event for orderId={}", event.getOrderId());

        try {
            assignDriverToOrder(event);
        } catch (Exception e) {
            log.error("❌ Fatal error processing order {}: {}", event.getOrderId(), e.getMessage(), e);
            // Don't rethrow — Kafka won't retry fatal errors by default
            // The retry is handled inside assignDriverToOrder
        }
    }

    // =========================================================
    // STEP 2: MAIN ASSIGNMENT LOGIC
    // =========================================================

    /**
     * Core dispatch method. Called by the Kafka consumer.
     *
     * What happens here:
     * 1. Find nearby drivers using Redis GEO
     * 2. Try each driver (sorted by distance)
     * 3. For each: acquire lock → attempt assignment
     * 4. If assignment fails → try next driver
     * 5. If all drivers fail → mark order for retry
     */
    @Transactional
    public void assignDriverToOrder(OrderCreatedEvent event) {

        // Validate order still exists and is in PENDING state
        Optional<Order> orderOpt = orderRepository.findById(event.getOrderId());
        if (orderOpt.isEmpty()) {
            log.warn("Order {} not found. Skipping assignment.", event.getOrderId());
            return;
        }

        Order order = orderOpt.get();
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is no longer PENDING (status={}). Skipping.", order.getId(), order.getStatus());
            return;
        }

        // Check retry limit
        if (order.getAssignmentAttempts() >= MAX_ASSIGNMENT_ATTEMPTS) {
            markOrderAsFailed(order, "Exceeded max assignment attempts");
            return;
        }

        // STEP 2a: Find nearby drivers from Redis GEO
        List<Long> nearbyDriverIds = findNearbyDriverIds(
                event.getPickupLatitude(),
                event.getPickupLongitude(),
                SEARCH_RADIUS_KM
        );

        log.info("🔍 Found {} nearby drivers for order {}", nearbyDriverIds.size(), order.getId());

        if (nearbyDriverIds.isEmpty()) {
            handleNoDriversAvailable(order);
            return;
        }

        // STEP 2b: Try to assign the best available driver
        boolean assigned = false;
        for (Long driverId : nearbyDriverIds) {
            assigned = tryAssignDriver(order, driverId, event);
            if (assigned) break;
        }

        if (!assigned) {
            handleNoDriversAvailable(order);
        }
    }

    // =========================================================
    // STEP 3: REDIS GEO — FIND NEARBY DRIVERS
    // =========================================================

    /**
     * Query Redis GEO index for drivers within radius.
     *
     * HOW REDIS GEO WORKS:
     * Redis uses a data structure called GeoHash (based on Geohash algorithm).
     * It encodes lat/lng into a sorted set score.
     * GEORADIUS does spatial math efficiently on this sorted set.
     *
     * COMMAND EQUIVALENT:
     * GEORADIUS driver:locations <lng> <lat> 5 km ASC COUNT 10
     *
     * Returns driver IDs sorted by distance (nearest first).
     */
    @SuppressWarnings("unchecked")
    private List<Long> findNearbyDriverIds(Double lat, Double lng, Double radiusKm) {
        try {
            GeoResults<RedisGeoCommands.GeoLocation<Object>> geoResults =
                    redisTemplate.opsForGeo().radius(
                            driverLocationKey,
                            new org.springframework.data.geo.Circle(
                                    new Point(lng, lat),  // Redis uses lng, lat order!
                                    new Distance(radiusKm, Metrics.KILOMETERS)
                            ),
                            RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                    .includeDistance()
                                    .sortAscending()  // Nearest first
                                    .limit(10)        // Max 10 candidates per order
                    );

            if (geoResults == null) return List.of();

            return geoResults.getContent()
                    .stream()
                    .map(result -> Long.parseLong(result.getContent().getName().toString()))
                    .toList();

        } catch (Exception e) {
            log.error("Redis GEO query failed: {}. Falling back to DB query.", e.getMessage());
            // FALLBACK: If Redis is down, query DB (slower but safe)
            return fallbackFindNearbyDrivers(lat, lng);
        }
    }

    /**
     * FALLBACK: If Redis GEO is unavailable, use DB with available drivers.
     * Less accurate (no distance sorting) but ensures system keeps working.
     * This is the "fault tolerance" in action!
     */
    private List<Long> fallbackFindNearbyDrivers(Double lat, Double lng) {
        log.warn("⚠️ Using DB fallback for driver search (Redis may be down)");
        return driverRepository.findActiveAvailableDrivers(
                        LocalDateTime.now().minusMinutes(5))
                .stream()
                .map(d -> d.getId())
                .toList();
    }

    // =========================================================
    // STEP 4: REDIS DISTRIBUTED LOCK + ASSIGNMENT
    // =========================================================

    /**
     * Try to assign a specific driver to an order.
     *
     * THE RACE CONDITION PROBLEM:
     * Without locking:
     *   Order#1 thread: "Is driver Raju available?" → Yes
     *   Order#2 thread: "Is driver Raju available?" → Yes (same time!)
     *   Order#1 thread: Assigns Raju to Order#1 ✅
     *   Order#2 thread: Also assigns Raju to Order#2 ❌ DOUBLE ASSIGNMENT!
     *
     * WITH REDIS LOCK:
     *   Order#1 thread: Acquires lock on "driver:lock:42" ✅ (SET NX)
     *   Order#2 thread: Tries to acquire same lock → FAILS (NX = Not Exists)
     *   Order#2 thread: Moves on to next driver candidate
     *   Order#1 thread: Does assignment, releases lock
     *
     * REDIS COMMAND: SET driver:lock:42 "order_1_timestamp" NX PX 10000
     * NX   = Only set if key does Not eXist (atomic!)
     * PX   = Expire in milliseconds (prevents permanent deadlock if app crashes)
     *
     * @return true if assignment succeeded, false if lock couldn't be acquired
     *         or driver is no longer available
     */
    private boolean tryAssignDriver(Order order, Long driverId, OrderCreatedEvent event) {
        String lockKey = driverLockPrefix + driverId;

        // ---- ACQUIRE REDIS LOCK ----
        // SetIfAbsent = SET NX (atomic: only sets if key doesn't exist)
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                "locked_by_order_" + order.getId(),
                lockTtlSeconds,
                TimeUnit.SECONDS
        );

        if (!Boolean.TRUE.equals(lockAcquired)) {
            // Another thread already locked this driver for another order
            log.debug("🔒 Driver {} is locked by another order. Skipping.", driverId);
            return false;
        }

        log.info("🔓 Acquired lock for driver {}. Attempting assignment to order {}.", driverId, order.getId());

        try {
            return performAssignment(order, driverId, event);
        } finally {
            // ---- ALWAYS RELEASE LOCK ----
            // Whether assignment succeeded or failed, release the lock
            // so other orders can try this driver
            // (The TTL also auto-releases it, but explicit release is faster)
            releaseLock(lockKey);
        }
    }

    /**
     * Actually perform the DB-level assignment after lock is acquired.
     *
     * ADDITIONAL SAFETY: DB Optimistic Locking
     * Even with Redis lock, we have the @Version field on Driver entity.
     * If two threads somehow bypass Redis lock → one gets an
     * OptimisticLockingFailureException → handled gracefully.
     *
     * Defense in depth: Redis lock (fast) + DB optimistic lock (safe)
     */
    @Transactional
    private boolean performAssignment(Order order, Long driverId, OrderCreatedEvent event) {
        // Fetch fresh driver state from DB (not stale cache)
        Optional<Driver> driverOpt = driverRepository.findById(driverId);

        if (driverOpt.isEmpty()) {
            log.warn("Driver {} not found in DB.", driverId);
            return false;
        }

        Driver driver = driverOpt.get();

        // Double-check: is driver actually available?
        // Redis might be slightly stale; DB is source of truth
        if (driver.getStatus() != DriverStatus.AVAILABLE) {
            log.info("Driver {} is no longer AVAILABLE (status={}). Skipping.", driverId, driver.getStatus());
            return false;
        }

        try {
            // ---- UPDATE DRIVER STATUS ----
            driver.setStatus(DriverStatus.BUSY);
            driverRepository.save(driver);
            // ^ If another thread modified this driver concurrently →
            //   OptimisticLockingFailureException thrown here

            // ---- UPDATE ORDER ----
            order.setDriver(driver);
            order.setStatus(OrderStatus.DRIVER_ASSIGNED);
            order.setAssignedAt(LocalDateTime.now());
            order.incrementAssignmentAttempts();
            orderRepository.save(order);

            log.info("✅ Successfully assigned driver {} to order {}", driverId, order.getId());

            // ---- POST-ASSIGNMENT NOTIFICATIONS ----
            notifyAssignment(order, driver, event);

            return true;

        } catch (ObjectOptimisticLockingFailureException e) {
            // OPTIMISTIC LOCK FAILED: Another thread updated this driver simultaneously
            // This is expected under high load → just move on to next driver
            log.warn("⚡ Optimistic lock conflict for driver {}. Another thread updated it. Skipping.", driverId);
            return false;
        }
    }

    // =========================================================
    // STEP 5: NOTIFY EVERYONE ABOUT THE ASSIGNMENT
    // =========================================================

    /**
     * After successful assignment:
     * 1. Push real-time WebSocket update to user and driver
     * 2. Publish DRIVER_ASSIGNED event to Kafka (for other services)
     */
    private void notifyAssignment(Order order, Driver driver, OrderCreatedEvent event) {

        // ---- WEBSOCKET: Real-time push to user ----
        // User subscribed to /topic/orders/123 gets this instantly
        var wsPayload = new java.util.HashMap<String, Object>();
        wsPayload.put("orderId", order.getId());
        wsPayload.put("status", "DRIVER_ASSIGNED");
        wsPayload.put("driverId", driver.getId());
        wsPayload.put("driverName", driver.getName());
        wsPayload.put("driverPhone", driver.getPhoneNumber());
        wsPayload.put("driverRating", driver.getRating());
        wsPayload.put("message", "Your driver " + driver.getName() + " is on the way!");
        wsPayload.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend(
                "/topic/orders/" + order.getId(),  // Channel: order-specific
                (Object) wsPayload
        );

        // ---- WEBSOCKET: Notify driver too ----
        var driverPayload = new java.util.HashMap<String, Object>();
        driverPayload.put("orderId", order.getId());
        driverPayload.put("pickupLat", order.getPickupLatitude());
        driverPayload.put("pickupLng", order.getPickupLongitude());
        driverPayload.put("deliveryLat", order.getDeliveryLatitude());
        driverPayload.put("deliveryLng", order.getDeliveryLongitude());
        driverPayload.put("message", "New order assigned! Please pick up.");

        messagingTemplate.convertAndSend(
                "/topic/drivers/" + driver.getId(),
                (Object)driverPayload
        );

        // ---- KAFKA: Publish DRIVER_ASSIGNED event ----
        // Other services (Notification, Restaurant, Tracking) subscribe to this
        DriverAssignedEvent assignedEvent = DriverAssignedEvent.builder()
                .orderId(order.getId())
                .driverId(driver.getId())
                .userId(event.getUserId())
                .driverName(driver.getName())
                .driverPhone(driver.getPhoneNumber())
                .driverRating(driver.getRating())
                .estimatedPickupMinutes(calculateETA(driver, order))
                .eventTimestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(driverAssignedTopic, assignedEvent);
        log.info("📡 Published DRIVER_ASSIGNED event for order {}", order.getId());
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    /**
     * Release Redis distributed lock by deleting the key.
     */
    private void releaseLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
            log.debug("🔓 Released lock: {}", lockKey);
        } catch (Exception e) {
            log.error("Failed to release lock {}: {}", lockKey, e.getMessage());
            // Lock TTL will auto-expire it anyway — no permanent deadlock
        }
    }

    /**
     * Handle case when no drivers are available.
     * Options:
     * 1. Increment retry count → retry later (via scheduled job)
     * 2. If max retries exceeded → mark as FAILED
     * 3. Notify user: "No drivers available right now"
     */
    private void handleNoDriversAvailable(Order order) {
        order.incrementAssignmentAttempts();

        if (order.getAssignmentAttempts() >= MAX_ASSIGNMENT_ATTEMPTS) {
            markOrderAsFailed(order, "No drivers available after max retries");
        } else {
            // Keep as PENDING → retry scheduler will pick it up
            orderRepository.save(order);
            log.info("🔄 No drivers for order {}. Will retry. Attempt={}/{}",
                    order.getId(), order.getAssignmentAttempts(), MAX_ASSIGNMENT_ATTEMPTS);

            // Notify user of temporary delay
            messagingTemplate.convertAndSend("/topic/orders/" + order.getId(),
                    (Object)java.util.Map.of(
                            "orderId", order.getId(),
                            "status", "SEARCHING",
                            "message", "Looking for a driver... Please wait."
                    ));
        }
    }

    private void markOrderAsFailed(Order order, String reason) {
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
        log.error("❌ Order {} FAILED: {}", order.getId(), reason);

        // Notify user
        messagingTemplate.convertAndSend("/topic/orders/" + order.getId(),
                (Object)java.util.Map.of(
                        "orderId", order.getId(),
                        "status", "FAILED",
                        "message", "Sorry, no drivers are available. Please try again."
                ));
    }

    /**
     * Rough ETA calculation in minutes.
     * In real world → use Google Maps Distance Matrix API
     */
    private Integer calculateETA(Driver driver, Order order) {
        if (driver.getCurrentLatitude() == null) return 10; // default

        double distKm = haversineDistanceKm(
                driver.getCurrentLatitude(), driver.getCurrentLongitude(),
                order.getPickupLatitude(), order.getPickupLongitude()
        );

        // Assume average speed of 30 km/h in city traffic
        return (int) Math.ceil((distKm / 30.0) * 60);
    }

    /**
     * HAVERSINE FORMULA: Calculate distance between two GPS coordinates.
     * Earth is a sphere → we can't just use Pythagoras (flat Earth assumption)
     * Haversine accounts for Earth's curvature.
     *
     * @return distance in kilometers
     */
    private double haversineDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371; // Earth's radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
