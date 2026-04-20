package com.delivery.system.service.impl;

import com.delivery.system.dto.DriverDTO;
import com.delivery.system.entity.Driver;
import com.delivery.system.enums.DriverStatus;
import com.delivery.system.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * =====================================================================
 * DRIVER SERVICE
 * =====================================================================
 *
 * Manages everything about drivers:
 * - Register a new driver
 * - Update driver location (called every ~5 sec from mobile app)
 * - Toggle driver availability (AVAILABLE ↔ OFFLINE)
 * - Find nearby drivers (for admin/debug purposes)
 *
 * KEY PATTERN: DUAL WRITE
 * When a driver updates their location, we write to TWO places:
 *   1. Redis GEO  → Fast nearest-driver query during dispatch
 *   2. PostgreSQL → Permanent record, history, reporting
 *
 * Redis is the "hot cache" for live queries.
 * PostgreSQL is the "source of truth" for persistent data.
 *
 * =====================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private final DriverRepository driverRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${redis.key.driver-location}")
    private String driverLocationKey;  // "driver:locations"

    // =========================================================
    // REGISTER DRIVER
    // =========================================================

    @Transactional
    public DriverDTO.DriverResponse registerDriver(DriverDTO.RegisterDriverRequest request) {
        // Check if email already registered
        if (driverRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Driver already registered with email: " + request.getEmail());
        }

        Driver driver = Driver.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .vehicleType(request.getVehicleType())
                .status(DriverStatus.OFFLINE)
                .rating(5.0)
                .totalDeliveries(0)
                .build();

        Driver saved = driverRepository.save(driver);
        log.info("✅ Registered new driver: id={}, name={}", saved.getId(), saved.getName());
        return toDriverResponse(saved);
    }

    // =========================================================
    // UPDATE DRIVER LOCATION (called every ~5 seconds from app)
    // =========================================================

    /**
     * Update driver's GPS location.
     *
     * TWO WRITES HAPPEN HERE:
     *
     * WRITE 1 → REDIS GEO (primary, fast):
     *   GEOADD driver:locations <lng> <lat> <driverId>
     *   This updates the sorted GEO set.
     *   Next GEORADIUS query will return this new position.
     *   Speed: ~1ms
     *
     * WRITE 2 → POSTGRESQL (secondary, persistent):
     *   UPDATE drivers SET lat=?, lng=?, updated_at=? WHERE id=?
     *   Slower but permanent.
     *   Used for: delivery history, driver analytics, Redis recovery.
     *   Speed: ~5-20ms
     *
     * WHY NOT JUST REDIS?
     * If Redis restarts → all location data is GONE.
     * PostgreSQL backup ensures we can rebuild Redis from DB.
     *
     * WHY NOT JUST POSTGRES?
     * Postgres spatial queries (even with PostGIS) are slower.
     * Under high load (1000 drivers updating/second) → Redis wins.
     */
    @Transactional
    public void updateDriverLocation(Long driverId, DriverDTO.LocationUpdateRequest request) {

        // ---- REDIS GEO WRITE ----
        // GEOADD command: Add/update driver's location in GEO sorted set
        // Key:    "driver:locations" (the GEO set name)
        // Member: driverId (identifies which driver this is)
        // Score:  internally computed from lat/lng using GeoHash
        redisTemplate.opsForGeo().add(
                driverLocationKey,
                new Point(request.getLongitude(), request.getLatitude()),  // Note: Redis uses lng, lat!
                String.valueOf(driverId)
        );

        // ---- POSTGRES WRITE ----
        driverRepository.updateDriverLocation(
                driverId,
                request.getLatitude(),
                request.getLongitude(),
                LocalDateTime.now()
        );

        log.debug("📍 Updated location for driver {}: lat={}, lng={}",
                driverId, request.getLatitude(), request.getLongitude());
    }

    // =========================================================
    // TOGGLE DRIVER AVAILABILITY
    // =========================================================

    /**
     * Driver goes ONLINE (AVAILABLE) or OFFLINE.
     *
     * When going ONLINE:
     *   - Status → AVAILABLE
     *   - Location must be in Redis for dispatch to find them
     *
     * When going OFFLINE:
     *   - Status → OFFLINE
     *   - Remove from Redis GEO set (so dispatch can't assign them)
     *   - Any active order should be reassigned (handled by order service)
     */
    @Transactional
    public DriverDTO.DriverResponse setDriverAvailability(Long driverId, boolean isAvailable) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));

        if (isAvailable) {
            driver.setStatus(DriverStatus.AVAILABLE);
            log.info("🟢 Driver {} is now AVAILABLE", driverId);
        } else {
            driver.setStatus(DriverStatus.OFFLINE);

            // Remove from Redis GEO set → dispatch won't find them
            redisTemplate.opsForGeo().remove(driverLocationKey, String.valueOf(driverId));
            log.info("🔴 Driver {} is now OFFLINE (removed from GEO index)", driverId);
        }

        Driver saved = driverRepository.save(driver);
        return toDriverResponse(saved);
    }

    // =========================================================
    // FIND NEARBY DRIVERS (for display / admin)
    // =========================================================

    /**
     * Find drivers within a radius of a given location.
     * Uses Redis GEO GEORADIUS command.
     *
     * Used by:
     * - Admin dashboard to see driver density
     * - GET /drivers/nearby API endpoint
     *
     * REDIS COMMAND EQUIVALENT:
     * GEORADIUS driver:locations <lng> <lat> <radius> km ASC COUNT 20
     * WITHCOORD → include actual coordinates
     * WITHDIST  → include distance from search point
     */
    @SuppressWarnings("unchecked")
    public List<DriverDTO.NearbyDriverInfo> findNearbyDrivers(
            Double lat, Double lng, Double radiusKm) {

        var geoResults = redisTemplate.opsForGeo().radius(
                driverLocationKey,
                new org.springframework.data.geo.Circle(
                        new Point(lng, lat),
                        new Distance(radiusKm, Metrics.KILOMETERS)
                ),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeCoordinates()   // Get actual lat/lng
                        .includeDistance()       // Get distance from query point
                        .sortAscending()
                        .limit(20)
        );

        if (geoResults == null) return List.of();

        return geoResults.getContent().stream().map(result -> {
                    Long driverId = Long.parseLong(result.getContent().getName().toString());
                    Double distKm = result.getDistance().getValue();

                    // Fetch driver details from DB
                    return driverRepository.findById(driverId)
                            .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
                            .map(d -> DriverDTO.NearbyDriverInfo.builder()
                                    .driverId(d.getId())
                                    .name(d.getName())
                                    .latitude(d.getCurrentLatitude())
                                    .longitude(d.getCurrentLongitude())
                                    .distanceKm(distKm)
                                    .rating(d.getRating())
                                    .vehicleType(d.getVehicleType())
                                    .build())
                            .orElse(null);
                })
                .filter(d -> d != null)
                .toList();
    }

    // =========================================================
    // GET DRIVER
    // =========================================================

    public DriverDTO.DriverResponse getDriver(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));
        return toDriverResponse(driver);
    }

    // =========================================================
    // MAPPER
    // =========================================================

    private DriverDTO.DriverResponse toDriverResponse(Driver driver) {
        return DriverDTO.DriverResponse.builder()
                .id(driver.getId())
                .name(driver.getName())
                .email(driver.getEmail())
                .phoneNumber(driver.getPhoneNumber())
                .vehicleType(driver.getVehicleType())
                .status(driver.getStatus().name())
                .currentLatitude(driver.getCurrentLatitude())
                .currentLongitude(driver.getCurrentLongitude())
                .rating(driver.getRating())
                .totalDeliveries(driver.getTotalDeliveries())
                .build();
    }
}
