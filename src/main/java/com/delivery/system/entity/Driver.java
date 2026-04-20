package com.delivery.system.entity;

import com.delivery.system.enums.DriverStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * =====================================================================
 * DRIVER ENTITY
 * =====================================================================
 *
 * Maps to the "drivers" table in PostgreSQL.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. WHY store location in BOTH Redis AND Postgres?
 *    - Redis GEO → Fast nearest-driver lookup (milliseconds)
 *    - Postgres   → Permanent record, for audit/history
 *    - Redis is the "working copy", Postgres is the "source of truth"
 *
 * 2. WHY have a "rating" field?
 *    - Dispatch logic can prefer higher-rated drivers (future feature)
 *    - A 4.8 rated driver > 3.2 rated driver for premium orders
 *
 * 3. WHY @Version (optimistic locking) on Driver too?
 *    - Multiple orders could try to assign this driver simultaneously
 *    - Version mismatch = one fails → that order re-tries with next driver
 *    - This is our DB-level safety net ON TOP OF Redis lock
 *
 * =====================================================================
 */
@Entity
@Table(name = "drivers", indexes = {
        @Index(name = "idx_drivers_status", columnList = "status"),
        @Index(name = "idx_drivers_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * OPTIMISTIC LOCKING: Same pattern as Order entity.
     * If two dispatch attempts try to mark same driver BUSY → one fails gracefully.
     */
    @Version
    private Long version;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    /**
     * VEHICLE TYPE: Bike, Car, Bicycle etc.
     * Could affect assignment for large/heavy orders.
     */
    @Column(name = "vehicle_type")
    private String vehicleType;

    /**
     * CURRENT STATUS: The dispatch service only considers AVAILABLE drivers.
     * This is the most critical field for the dispatch algorithm.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DriverStatus status = DriverStatus.OFFLINE;

    /**
     * LAST KNOWN LOCATION: Stored in DB for persistence.
     * The "live" location lives in Redis GEO for fast queries.
     */
    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    /**
     * WHEN was location last updated?
     * If > 5 minutes ago → driver might be offline, skip them.
     */
    @Column(name = "last_location_update")
    private LocalDateTime lastLocationUpdate;

    /**
     * DRIVER RATING: 1.0 to 5.0
     * Used in advanced dispatch (prefer higher rated drivers)
     * Default = 5.0 for new drivers (benefit of the doubt)
     */
    @Column(name = "rating")
    @Builder.Default
    private Double rating = 5.0;

    /**
     * TOTAL DELIVERIES: How many orders has this driver completed?
     * Experience metric. Used to validate rating credibility.
     */
    @Column(name = "total_deliveries")
    @Builder.Default
    private Integer totalDeliveries = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
