package com.delivery.system.enums;
/**
 * =====================================================================
 * ORDER STATUS ENUM
 * =====================================================================
 *
 * WHAT IS AN ENUM?
 * A fixed set of named constants. Think of it as a dropdown menu
 * with predefined options. Prevents typos like "PENIDNG" instead of "PENDING".
 *
 * WHY DO WE NEED THIS?
 * An order goes through a lifecycle. This enum tracks which stage it's in.
 *
 * ORDER LIFECYCLE:
 *
 *   PENDING → DRIVER_ASSIGNED → PICKED_UP → DELIVERED
 *       ↘                                        ↗
 *         CANCELLED (can happen at most stages)
 *
 * =====================================================================
 */
public enum OrderStatus {

    /**
     * PENDING: Order just placed by user.
     * Dispatch service will now find a driver.
     */
    PENDING,

    /**
     * DRIVER_ASSIGNED: A driver has accepted this order.
     * User gets notified via WebSocket.
     */
    DRIVER_ASSIGNED,

    /**
     * PICKED_UP: Driver picked up food from restaurant.
     * On the way to user now.
     */
    PICKED_UP,

    /**
     * DELIVERED: Order successfully delivered. 🎉
     * Driver becomes AVAILABLE again.
     */
    DELIVERED,

    /**
     * CANCELLED: Order cancelled by user or system.
     * Happens when no drivers available or user cancels.
     */
    CANCELLED,

    /**
     * FAILED: Assignment failed after all retries.
     * Notify user; log for ops team.
     */
    FAILED
}
