package com.delivery.system.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * =====================================================================
 * DRIVER DTOs
 * =====================================================================
 */
public class DriverDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterDriverRequest {

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100)
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[+]?[0-9]{10,13}$", message = "Invalid phone number")
        private String phoneNumber;

        private String vehicleType;
    }

    /**
     * Location update request.
     * Drivers send this every ~5 seconds from the mobile app.
     * We update BOTH Redis GEO (for dispatch) AND Postgres (for history).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationUpdateRequest {

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        private Double latitude;

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        private Double longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverResponse {
        private Long id;
        private String name;
        private String email;
        private String phoneNumber;
        private String vehicleType;
        private String status;
        private Double currentLatitude;
        private Double currentLongitude;
        private Double rating;
        private Integer totalDeliveries;
    }

    /**
     * Nearby driver info returned by GET /drivers/nearby
     * Includes distance so the dispatch logic (and client UI) knows how far.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NearbyDriverInfo {
        private Long driverId;
        private String name;
        private Double latitude;
        private Double longitude;
        private Double distanceKm;
        private Double rating;
        private String vehicleType;
    }
}