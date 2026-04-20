package com.delivery.system.entity;

import com.delivery.system.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * =====================================================================
 * ORDER ENTITY
 * =====================================================================
 *
 * WHAT IS AN ENTITY?
 * A Java class that maps directly to a database table.
 * Each field = one column in the "orders" table.
 * Each instance (object) = one row in the table.
 *
 * ANNOTATIONS EXPLAINED:
 * @Entity         → "Hey JPA/Hibernate, this class maps to a DB table"
 * @Table           → Specifies the actual table name in PostgreSQL
 * @Id             → This field is the Primary Key
 * @GeneratedValue → DB auto-generates this value (auto-increment)
 * @Column         → Optional customization of column behavior
 * @Version        → OPTIMISTIC LOCKING (explained below)
 *
 * OPTIMISTIC LOCKING (The "version" field):
 * Problem: Two threads try to update the same order simultaneously.
 *   Thread A reads order (version=1), Thread B reads order (version=1)
 *   Thread A saves (version becomes 2)
 *   Thread B tries to save with version=1 → FAILS (version mismatch)
 * This prevents silent data corruption! Hibernate throws
 * OptimisticLockException if two updates conflict.
 *
 * =====================================================================
 */
@Entity
@Table(name = "orders", indexes = {
        // Indexes speed up queries that filter by these columns
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_user_id", columnList = "user_id"),
        @Index(name = "idx_orders_driver_id", columnList = "driver_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    /**
     * PRIMARY KEY: Unique identifier for each order.
     * IDENTITY strategy = PostgreSQL auto-increments this.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * OPTIMISTIC LOCK VERSION:
     * Hibernate automatically manages this column.
     * Increments on every UPDATE. Prevents concurrent modification bugs.
     */
    @Version
    private Long version;

    /**
     * USER who placed this order.
     * We store just the ID (not the full User object) to keep it simple.
     * In a microservice world, User details live in a separate User Service.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * DRIVER assigned to deliver this order.
     * NULL until dispatch assigns a driver.
     * Gets set when status changes to DRIVER_ASSIGNED.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    /**
     * CURRENT STATUS in the order lifecycle.
     * Starts as PENDING, progresses through the lifecycle.
     *
     * EnumType.STRING → Store "PENDING" in DB (not 0, 1, 2...)
     * → Much easier to read and debug in DB
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /**
     * RESTAURANT where food is being ordered from.
     */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    /**
     * PICKUP LOCATION: Where driver picks up food (restaurant coordinates).
     */
    @Column(name = "pickup_latitude", nullable = false)
    private Double pickupLatitude;

    @Column(name = "pickup_longitude", nullable = false)
    private Double pickupLongitude;

    /**
     * DELIVERY LOCATION: Where driver drops off food (user's address).
     */
    @Column(name = "delivery_latitude", nullable = false)
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude", nullable = false)
    private Double deliveryLongitude;

    /**
     * ORDER AMOUNT in INR (or your local currency).
     * BigDecimal for money → avoids floating point precision errors.
     * NEVER use double/float for money!
     */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * HOW MANY TIMES have we tried to assign a driver?
     * If > MAX_RETRIES → mark as FAILED
     * Default = 0 (no retries yet)
     */
    @Column(name = "assignment_attempts", nullable = false)
    @Builder.Default
    private Integer assignmentAttempts = 0;

    /**
     * TIMESTAMPS: Automatically set by Hibernate.
     * updatable = false → createdAt never changes after INSERT
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * WHEN was this order assigned to a driver?
     * Used to calculate estimated delivery time.
     */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /**
     * SPECIAL INSTRUCTIONS from user (e.g., "No spice", "Ring doorbell").
     */
    @Column(name = "special_instructions", length = 500)
    private String specialInstructions;

    // =========================================================
    // HELPER METHOD: Clean way to increment retry count
    // =========================================================
    public void incrementAssignmentAttempts() {
        this.assignmentAttempts++;
    }
}