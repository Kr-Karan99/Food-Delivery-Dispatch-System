package com.delivery.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * =====================================================================
 * WEBSOCKET CONFIGURATION (using STOMP over WebSocket)
 * =====================================================================
 *
 * WHAT IS WEBSOCKET?
 * A persistent, full-duplex connection between client and server.
 * Unlike HTTP (where client ALWAYS initiates), WebSocket allows
 * SERVER to PUSH data to the client whenever it wants.
 *
 * HTTP (polling — old way):
 *   Client: "Any updates?" → Server: "No."
 *   Client: "Any updates?" → Server: "No."
 *   Client: "Any updates?" → Server: "Yes! Driver assigned!"
 *   (Wasteful — spams the server with requests)
 *
 * WebSocket (push — our way):
 *   Client connects once → stays connected
 *   Server: "Driver assigned!" (pushes whenever there's news)
 *   (Efficient — instant updates, no polling)
 *
 * WHAT IS STOMP?
 * Simple Text Oriented Messaging Protocol.
 * A messaging protocol that runs ON TOP of WebSocket.
 * Think: HTTP is to TCP as STOMP is to WebSocket.
 * STOMP gives us:
 *   - Topics (pub/sub)
 *   - Message routing
 *   - Headers
 *
 * OUR SETUP:
 * - Client connects to: ws://localhost:8080/ws
 * - Server pushes to:   /topic/orders/{orderId}
 * - Client subscribes:  /topic/orders/123  (for order 123 updates)
 *
 * =====================================================================
 */
@Configuration
@EnableWebSocketMessageBroker  // Enable WebSocket with STOMP message broker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure the STOMP message broker.
     *
     * enableSimpleBroker("/topic") → In-memory broker for pub/sub.
     *   All messages sent to /topic/... are broadcast to subscribers.
     *   (Replace with a real broker like RabbitMQ for production scale)
     *
     * setApplicationDestinationPrefixes("/app") → Messages FROM client
     *   that start with /app go to @MessageMapping methods in controllers.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Clients subscribe to /topic/... to receive updates
        registry.enableSimpleBroker("/topic");

        // Clients send messages to /app/... routes (if needed)
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Register the WebSocket endpoint.
     *
     * Clients connect to ws://localhost:8080/ws to establish the connection.
     * withSockJS() → Fallback for browsers that don't support WebSocket
     *               (uses long-polling, streaming as fallback)
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws")        // WebSocket handshake URL
                .setAllowedOriginPatterns("*")  // Allow all origins (restrict in prod!)
                .withSockJS();             // Enable SockJS fallback
    }

    /*
     * HOW CLIENTS USE THIS:
     *
     * JavaScript / Mobile Client:
     *   const socket = new SockJS('http://localhost:8080/ws');
     *   const stompClient = Stomp.over(socket);
     *
     *   stompClient.connect({}, () => {
     *     // Subscribe to updates for order 123
     *     stompClient.subscribe('/topic/orders/123', (message) => {
     *       const update = JSON.parse(message.body);
     *       console.log('Order update:', update);
     *     });
     *   });
     */
}