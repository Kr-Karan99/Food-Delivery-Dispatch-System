package com.delivery.system.controller;



import com.delivery.system.dto.DriverDTO;
import com.delivery.system.service.impl.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * =====================================================================
 * DRIVER CONTROLLER
 * =====================================================================
 *
 * REST endpoints for driver operations.
 * Base path: /api/v1/drivers
 *
 * ENDPOINTS:
 *   POST   /api/v1/drivers              → Register a new driver
 *   GET    /api/v1/drivers/{id}         → Get driver profile
 *   POST   /api/v1/drivers/location     → Update driver GPS location
 *   PATCH  /api/v1/drivers/availability → Go online/offline
 *   GET    /api/v1/drivers/nearby       → Find nearby available drivers
 *
 * =====================================================================
 */
@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@Slf4j
public class DriverController {


    private final DriverService driverService;

    // =========================================================
    // POST /api/v1/drivers — Register Driver
    // =========================================================

    @PostMapping
    public ResponseEntity<DriverDTO.DriverResponse> registerDriver(
            @Valid @RequestBody DriverDTO.RegisterDriverRequest request
    ) {
        log.info("🚗 POST /drivers - Registering: {}", request.getEmail());
        DriverDTO.DriverResponse response = driverService.registerDriver(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================
    // GET /api/v1/drivers/{driverId} — Get Driver Profile
    // =========================================================

    @GetMapping("/{driverId}")
    public ResponseEntity<DriverDTO.DriverResponse> getDriver(@PathVariable Long driverId) {
        log.info("🔍 GET /drivers/{}", driverId);
        return ResponseEntity.ok(driverService.getDriver(driverId));
    }

    // =========================================================
    // POST /api/v1/drivers/location — Update Location
    // =========================================================

    /**
     * Called by mobile app every ~5 seconds while driver is active.
     * Updates Redis GEO (for fast dispatch) + PostgreSQL (for persistence).
     *
     * HIGH FREQUENCY endpoint: Could get 1000 req/sec with 200 active drivers.
     * That's why Redis handles this — sub-millisecond writes.
     *
     * In production: Consider using WebSocket for location updates too
     * (persistent connection is more efficient than repeated HTTP calls).
     */
    @PostMapping("/location")
    public ResponseEntity<Void> updateLocation(
            @RequestHeader("X-Driver-Id") Long driverId,
            @Valid @RequestBody DriverDTO.LocationUpdateRequest request
    ) {
        driverService.updateDriverLocation(driverId, request);
        return ResponseEntity.ok().build();
    }

    // =========================================================
    // PATCH /api/v1/drivers/availability — Go Online/Offline
    // =========================================================

    /**
     * Driver toggles their availability.
     *
     * @RequestParam → URL query parameter
     * e.g., PATCH /api/v1/drivers/availability?available=true
     *
     * Going ONLINE:  status → AVAILABLE, appear in Redis GEO
     * Going OFFLINE: status → OFFLINE,   removed from Redis GEO
     */
    @PatchMapping("/availability")
    public ResponseEntity<DriverDTO.DriverResponse> setAvailability(
            @RequestHeader("X-Driver-Id") Long driverId,
            @RequestParam boolean available
    ) {
        log.info("🔄 Driver {} setting availability={}", driverId, available);
        DriverDTO.DriverResponse response = driverService.setDriverAvailability(driverId, available);
        return ResponseEntity.ok(response);
    }

    // =========================================================
    // GET /api/v1/drivers/nearby — Find Nearby Drivers
    // =========================================================

    /**
     * Find available drivers near a location.
     *
     * @RequestParam → URL query parameters
     * e.g., GET /api/v1/drivers/nearby?lat=12.97&lng=77.59&radius=5
     *
     * Used by:
     * - Admin dashboard to visualize driver distribution
     * - App to show "X drivers near you" before ordering
     *
     * Powered by Redis GEORADIUS command → extremely fast!
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<DriverDTO.NearbyDriverInfo>> getNearbyDrivers(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "5.0") Double radius
    ) {
        log.info("📍 GET /drivers/nearby - lat={}, lng={}, radius={}km", lat, lng, radius);
        List<DriverDTO.NearbyDriverInfo> drivers = driverService.findNearbyDrivers(lat, lng, radius);
        return ResponseEntity.ok(drivers);
    }
}
