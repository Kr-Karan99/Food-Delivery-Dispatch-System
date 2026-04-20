package com.delivery.system.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * =====================================================================
 * REDIS CONFIGURATION
 * =====================================================================
 *
 * WHAT IS REDIS?
 * An in-memory key-value store. Like a giant HashMap that lives outside
 * your application — lightning fast (sub-millisecond), shared across
 * all application instances.
 *
 * HOW WE USE REDIS IN THIS SYSTEM:
 *
 * 1. 🗺️ GEO INDEX (Driver Locations)
 *    Redis command: GEOADD driver:locations <lng> <lat> <driverId>
 *    Query:         GEORADIUS driver:locations <lng> <lat> 5 km ASC COUNT 10
 *    Result:        Sorted list of nearest drivers in milliseconds!
 *
 * 2. 🔒 DISTRIBUTED LOCK (Prevent double-assignment)
 *    Redis command: SET driver:lock:42 "locked" NX PX 10000
 *    NX = "only set if NOT EXISTS" (atomic! No race condition)
 *    PX 10000 = expire in 10 seconds (prevents deadlock if app crashes)
 *
 * 3. 💾 CACHING (Driver availability)
 *    Store driver status in Redis to avoid DB hits on every dispatch.
 *
 * WHY REDIS OVER DB FOR THESE?
 * - GEO queries in Redis = microseconds
 * - GEO queries in Postgres = milliseconds to seconds (need PostGIS)
 * - Distributed locks need atomicity that Redis provides natively
 *
 * =====================================================================
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate<String, Object>
     * → Key type: String   (e.g., "driver:lock:42")
     * → Value type: Object (could be String, JSON, Number etc.)
     *
     * Think of it as: an injected "Redis client" that we autowire
     * into our services to run Redis commands.
     *
     * SERIALIZERS:
     * - StringRedisSerializer for keys → store keys as plain strings
     * - GenericJackson2JsonRedisSerializer for values → store values as JSON
     *   (This lets us store/retrieve any Java object as JSON in Redis)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key serializer: store keys as plain UTF-8 strings
        // e.g., "driver:lock:42" not some binary garbage
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value serializer: store values as JSON
        // e.g., {"name":"Raju","status":"AVAILABLE"} → readable in Redis CLI
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}
