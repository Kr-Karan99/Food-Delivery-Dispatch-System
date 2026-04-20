package com.delivery.system.controller;


import com.delivery.system.dto.OrderDTO;
import com.delivery.system.service.impl.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * =====================================================================
 * ORDER CONTROLLER
 * =====================================================================
 *
 * WHAT IS A CONTROLLER?
 * The "front door" of our application.
 * It receives HTTP requests, delegates to Services, returns responses.
 *
 * CONTROLLER RULE: Keep it THIN!
 * Controllers should only:
 *   1. Parse incoming request
 *   2. Call the right service method
 *   3. Return the response
 * ALL business logic lives in the Service layer, NOT here.
 *
 * ANNOTATIONS EXPLAINED:
 * @RestController = @Controller + @ResponseBody
 *   → This class handles HTTP requests
 *   → Every method's return value is serialized to JSON automatically
 *
 * @RequestMapping("/api/v1/orders")
 *   → All endpoints in this class start with /api/v1/orders
 *   → "/v1" = API versioning (if we change the API later, create /v2 without breaking /v1)
 *
 * HTTP METHODS:
 *   POST   → Create something new      (create order)
 *   GET    → Read/fetch data           (get order, list orders)
 *   PUT    → Update entire resource    (replace order)
 *   PATCH  → Partial update            (cancel order)
 *   DELETE → Remove resource
 *
 * HTTP STATUS CODES:
 *   200 OK           → Success with body
 *   201 CREATED      → Resource successfully created
 *   400 BAD REQUEST  → Client sent invalid data
 *   401 UNAUTHORIZED → Not logged in
 *   403 FORBIDDEN    → Logged in but not allowed
 *   404 NOT FOUND    → Resource doesn't exist
 *   500 SERVER ERROR → Something crashed on our side
 *
 * =====================================================================
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    // =========================================================
    // POST /api/v1/orders
    // Create a new order
    // =========================================================

    /**
     * Create a new food delivery order.
     *
     * @RequestBody  → Parse JSON from request body into CreateOrderRequest object
     * @Valid        → Run validation annotations (@NotNull, @Positive etc.)
     *                 If validation fails → Spring returns 400 automatically
     * @RequestHeader → Extract userId from JWT (simplified; real apps use SecurityContext)
     *
     * FLOW:
     * 1. User calls POST /api/v1/orders with order details
     * 2. Validation runs (coordinates, amount etc.)
     * 3. OrderService creates order + fires Kafka event
     * 4. We return 201 CREATED with order details (status=PENDING)
     * 5. DispatchService (async) assigns driver → pushes via WebSocket
     *
     * RESPONSE:
     * User immediately gets their order ID back (status=PENDING).
     * Driver assignment happens in background — user gets WebSocket update.
     * This is ASYNC pattern — faster user experience!
     */
    @PostMapping
    public ResponseEntity<OrderDTO.OrderResponse> createOrder(
            @Valid @RequestBody OrderDTO.CreateOrderRequest request,
            @RequestHeader("X-User-Id") Long userId  // In production: extract from JWT
    ) {
        log.info("🛒 POST /orders - userId={}", userId);
        OrderDTO.OrderResponse response = orderService.createOrder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================
    // GET /api/v1/orders/{orderId}
    // Get a specific order
    // =========================================================

    /**
     * @PathVariable → Extract {orderId} from the URL
     * e.g., GET /api/v1/orders/42 → orderId = 42
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDTO.OrderResponse> getOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        log.info("🔍 GET /orders/{} - userId={}", orderId, userId);
        OrderDTO.OrderResponse response = orderService.getOrder(orderId, userId);
        return ResponseEntity.ok(response);
    }

    // =========================================================
    // GET /api/v1/orders
    // List all orders for the authenticated user
    // =========================================================

    @GetMapping
    public ResponseEntity<List<OrderDTO.OrderResponse>> getUserOrders(
            @RequestHeader("X-User-Id") Long userId
    ) {
        log.info("📋 GET /orders - userId={}", userId);
        List<OrderDTO.OrderResponse> orders = orderService.getUserOrders(userId);
        return ResponseEntity.ok(orders);
    }

    // =========================================================
    // PATCH /api/v1/orders/{orderId}/cancel
    // Cancel an order
    // =========================================================

    /**
     * PATCH (not DELETE) because we're not deleting — just changing status.
     * /cancel in URL makes the intent crystal clear.
     */
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderDTO.OrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        log.info("🚫 PATCH /orders/{}/cancel - userId={}", orderId, userId);
        OrderDTO.OrderResponse response = orderService.cancelOrder(orderId, userId);
        return ResponseEntity.ok(response);
    }

    /*
     * ================================================================
     * INTERVIEW TALKING POINTS FOR THIS CLASS:
     * ================================================================
     *
     * Q: Why separate Controller and Service?
     * A: Single Responsibility. Controller handles HTTP concerns.
     *    Service handles business logic. Easy to test separately.
     *    If we add a GraphQL layer, we reuse the same Service — no duplication.
     *
     * Q: Why return ResponseEntity instead of just the DTO?
     * A: ResponseEntity gives us control over HTTP status code.
     *    201 vs 200 matters — 201 tells client "a new resource was created."
     *    Also lets us add custom headers if needed.
     *
     * Q: Why /api/v1/ prefix?
     * A: API versioning. When we break the API, we create /v2 endpoints.
     *    Existing clients on /v1 keep working. Smooth migration.
     *
     * Q: How is userId passed safely?
     * A: In production, it comes from a decoded JWT token via Spring Security.
     *    The X-User-Id header here is for simplicity in explanation.
     *    Real implementation: extract from SecurityContextHolder.
     * ================================================================
     */
}
