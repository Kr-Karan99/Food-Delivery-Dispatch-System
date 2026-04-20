package com.delivery.system.event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * =====================================================================
 * KAFKA EVENT: DriverAssignedEvent
 * =====================================================================
 *
 * Published AFTER a driver has been successfully assigned to an order.
 *
 * WHO CONSUMES THIS?
 * 1. NotificationService → Send push notification to user's phone
 * 2. TrackingService     → Start tracking driver's location
 * 3. RestaurantService   → Notify restaurant: "Driver is coming!"
 *
 * WHY PUBLISH AN EVENT INSTEAD OF CALLING EACH SERVICE?
 * If we called them directly → OrderService needs to know about ALL services.
 * With events → services subscribe to what they care about. Decoupled!
 *
 * New service wants to react to assignment? → Just subscribe to this topic.
 * No code change needed in DispatchService. 🎉
 *
 * =====================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverAssignedEvent {

    private Long orderId;
    private Long driverId;
    private Long userId;

    private String driverName;
    private String driverPhone;
    private Double driverRating;

    /** How far is driver from restaurant? In meters. */
    private Double distanceToPickupMeters;

    /** Estimated time to pick up food (in minutes) */
    private Integer estimatedPickupMinutes;

    private LocalDateTime eventTimestamp;
}
