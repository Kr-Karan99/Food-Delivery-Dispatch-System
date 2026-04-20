package com.delivery.system.repository;


import com.delivery.system.entity.Driver;
import com.delivery.system.enums.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * =====================================================================
 * DRIVER REPOSITORY
 * =====================================================================
 *
 * Data access for Driver entity.
 *
 * IMPORTANT NOTE ON DRIVER LOCATION:
 * - DB stores location for persistence (history, audit)
 * - Redis GEO stores location for fast proximity queries
 *
 * When dispatch needs "nearest driver" → Use Redis (microseconds)
 * When we need to display a driver's history → Use DB
 *
 * =====================================================================
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    /**
     * Find all available drivers.
     * This is used as a FALLBACK if Redis is down.
     * Redis is the primary source for nearby queries.
     */
    List<Driver> findByStatus(DriverStatus status);

    /**
     * Find driver by email (used during login / auth).
     */
    Optional<Driver> findByEmail(String email);

    /**
     * Find available drivers whose location was updated recently.
     * Avoids assigning drivers who may have gone offline without updating status.
     *
     * "lastLocationUpdate > :cutoffTime" → Only drivers active in last 5 minutes
     */
    @Query("SELECT d FROM Driver d WHERE d.status = 'AVAILABLE' AND d.lastLocationUpdate > :cutoffTime")
    List<Driver> findActiveAvailableDrivers(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Update driver status in bulk.
     * Used when a driver goes offline → mark all their pending work as needing reassignment.
     */
    @Modifying
    @Query("UPDATE Driver d SET d.status = :status WHERE d.id = :driverId")
    int updateDriverStatus(@Param("driverId") Long driverId, @Param("status") DriverStatus status);

    /**
     * Update location in DB (Redis gets updated separately and faster).
     */
    @Modifying
    @Query("UPDATE Driver d SET d.currentLatitude = :lat, d.currentLongitude = :lng, d.lastLocationUpdate = :updateTime WHERE d.id = :driverId")
    int updateDriverLocation(
            @Param("driverId") Long driverId,
            @Param("lat") Double lat,
            @Param("lng") Double lng,
            @Param("updateTime") LocalDateTime updateTime
    );
}
