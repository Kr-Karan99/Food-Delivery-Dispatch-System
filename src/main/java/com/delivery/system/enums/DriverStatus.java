package com.delivery.system.enums;

/**
 * =====================================================================
 * DRIVER STATUS ENUM
 * =====================================================================
 *
 * Tracks what a driver is currently doing.
 *
 * DRIVER LIFECYCLE:
 *
 *   OFFLINE → AVAILABLE → BUSY → AVAILABLE (cycle repeats)
 *                ↘
 *             ON_BREAK
 *
 * WHY IS THIS IMPORTANT FOR DISPATCH?
 * We ONLY assign orders to drivers whose status = AVAILABLE.
 * The Redis lock + DB update together flip status to BUSY atomically.
 *
 * =====================================================================
 */
public enum DriverStatus {

    /**
     * OFFLINE: Driver has logged out of the app.
     * Not eligible for any assignments.
     */
    OFFLINE,

    /**
     * AVAILABLE: Driver is online and ready to accept orders.
     * This is the ONLY status that makes a driver assignable.
     */
    AVAILABLE,

    /**
     * BUSY: Driver currently has an active delivery.
     * Will become AVAILABLE again after delivery is complete.
     */
    BUSY,

    /**
     * ON_BREAK: Driver is temporarily not accepting orders.
     * Driver chose this manually in the app.
     */
    ON_BREAK
}
