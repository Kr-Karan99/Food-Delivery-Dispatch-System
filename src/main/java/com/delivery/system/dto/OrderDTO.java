package com.delivery.system.dto;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * =====================================================================
 * REQUEST DTOs (Data Transfer Objects)
 * =====================================================================
 *
 * WHAT IS A DTO?
 * A simple object that carries data between layers.
 * Think of it as a "form" that users fill out when making requests.
 *
 * WHY NOT JUST USE THE ENTITY DIRECTLY?
 * 1. SECURITY: Entity has fields we don't want users to set
 *    (e.g., userId, version, createdAt — these are set by the system)
 * 2. VALIDATION: DTOs have input validation annotations
 * 3. FLEXIBILITY: API shape can differ from DB shape
 * 4. DECOUPLING: DB changes don't break the API contract
 *
 * =====================================================================
 */
public class OrderDTO {

    /**
     * REQUEST DTO: What the client sends to create an order.
     * POST /orders body.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {

        /**
         * @NotNull → Field must be present (not null)
         * @Positive → Must be a positive number
         * These validations run BEFORE the controller method is called.
         * If they fail → 400 Bad Request is returned automatically.
         */
        @NotNull(message = "Restaurant ID is required")
        @Positive(message = "Restaurant ID must be positive")
        private Long restaurantId;

        @NotNull(message = "Pickup latitude is required")
        @DecimalMin(value = "-90.0", message = "Invalid latitude")
        @DecimalMax(value = "90.0", message = "Invalid latitude")
        private Double pickupLatitude;

        @NotNull(message = "Pickup longitude is required")
        @DecimalMin(value = "-180.0", message = "Invalid longitude")
        @DecimalMax(value = "180.0", message = "Invalid longitude")
        private Double pickupLongitude;

        @NotNull(message = "Delivery latitude is required")
        @DecimalMin(value = "-90.0", message = "Invalid latitude")
        @DecimalMax(value = "90.0", message = "Invalid latitude")
        private Double deliveryLatitude;

        @NotNull(message = "Delivery longitude is required")
        @DecimalMin(value = "-180.0", message = "Invalid longitude")
        @DecimalMax(value = "180.0", message = "Invalid longitude")
        private Double deliveryLongitude;

        @NotNull(message = "Total amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        private BigDecimal totalAmount;

        @Size(max = 500, message = "Instructions too long")
        private String specialInstructions;
    }

    /**
     * RESPONSE DTO: What we send back to the client.
     * We control exactly what data is exposed — never expose internal details!
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private Long id;
        private Long userId;
        private String status;
        private Long restaurantId;
        private Double pickupLatitude;
        private Double pickupLongitude;
        private Double deliveryLatitude;
        private Double deliveryLongitude;
        private BigDecimal totalAmount;
        private String specialInstructions;
        private Integer assignmentAttempts;
        private String createdAt;
        private String updatedAt;

        // Driver info (populated after assignment)
        private DriverInfo assignedDriver;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DriverInfo {
            private Long driverId;
            private String driverName;
            private String driverPhone;
            private Double driverRating;
            private Double distanceKm;
        }
    }
}