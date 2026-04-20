package com.delivery.system.repository;

import com.delivery.system.entity.Order;
import com.delivery.system.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;//check
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * =====================================================================
 * ORDER REPOSITORY
 * =====================================================================
 *
 * WHAT IS A REPOSITORY?
 * It's the DATA ACCESS LAYER — the only part of our code that
 * directly talks to the database.
 *
 * MAGIC OF JpaRepository:
 * By extending JpaRepository<Order, Long> we get these FOR FREE:
 *   - save(order)          → INSERT or UPDATE
 *   - findById(id)         → SELECT * FROM orders WHERE id = ?
 *   - findAll()            → SELECT * FROM orders
 *   - deleteById(id)       → DELETE FROM orders WHERE id = ?
 *   - count()              → SELECT COUNT(*) FROM orders
 *   - existsById(id)       → Returns boolean
 *   ... and many more!
 *
 * QUERY METHODS (Spring Data Magic):
 * Spring reads the METHOD NAME and generates SQL automatically!
 *   findByStatus(status) → SELECT * FROM orders WHERE status = ?
 *   findByUserId(userId) → SELECT * FROM orders WHERE user_id = ?
 *
 * CUSTOM QUERIES:
 * For complex queries, use @Query with JPQL (or nativeQuery=true for raw SQL)
 * JPQL uses class names/field names, not table names.
 *
 * =====================================================================
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // ---------------------------------------------------------------
    // DERIVED QUERY METHODS (Spring generates SQL from method name)
    // ---------------------------------------------------------------

    /**
     * Find all orders for a specific user.
     * Generated SQL: SELECT * FROM orders WHERE user_id = ?
     */
    List<Order> findByUserId(Long userId);

    /**
     * Find all orders with a specific status.
     * Generated SQL: SELECT * FROM orders WHERE status = ?
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * Find all orders for a specific driver.
     * Generated SQL: SELECT * FROM orders WHERE driver_id = ?
     */
    List<Order> findByDriverId(Long driverId);

    /**
     * Find orders by user + status (two conditions).
     * Generated SQL: SELECT * FROM orders WHERE user_id = ? AND status = ?
     */
    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    // ---------------------------------------------------------------
    // CUSTOM JPQL QUERIES
    // ---------------------------------------------------------------

    /**
     * Find PENDING orders that have been retried fewer than maxAttempts times.
     * Used by the dispatch retry mechanism.
     *
     * JPQL Note: "o" is an alias for Order entity.
     * We use field names (assignmentAttempts), not column names (assignment_attempts).
     */
    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING' AND o.assignmentAttempts < :maxAttempts ORDER BY o.createdAt ASC")
    List<Order> findPendingOrdersForRetry(@Param("maxAttempts") int maxAttempts);

    /**
     * PESSIMISTIC LOCK: Lock the order row during assignment.
     *
     * SELECT ... FOR UPDATE → Tells DB: "I'm going to update this row,
     * don't let anyone else touch it until my transaction commits."
     *
     * WHY? During driver assignment, we need to ensure no other
     * thread assigns a different driver to this same order.
     *
     * This is our DB-level lock (Redis lock handles driver-level concurrency).
     */
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    /**
     * Bulk update: Mark all orders for a driver as FAILED
     * when a driver goes offline unexpectedly.
     *
     * @Modifying → Required for UPDATE/DELETE queries in Spring Data
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = 'FAILED' WHERE o.driver.id = :driverId AND o.status = 'DRIVER_ASSIGNED'")
    int failOrdersForDriver(@Param("driverId") Long driverId);
}