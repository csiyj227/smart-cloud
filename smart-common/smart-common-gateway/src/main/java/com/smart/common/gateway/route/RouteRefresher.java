package com.smart.common.gateway.route;

import com.smart.common.core.cache.CacheKeyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Dynamic route refresher using Redis Stream for change notification.
 *
 * On startup: loads all route definitions from Redis Hash.
 * On change: listens to Redis Stream for route update events.
 *
 * This replaces the pub/sub approach with Redis Stream for more reliable
 * message delivery (Stream persists messages, pub/sub does not).
 *
 * 使用 Redis Stream 实现变更通知的动态路由刷新器。
 *
 * 启动时：从 Redis Hash 加载所有路由定义。
 * 变更时：监听 Redis Stream 接收路由更新事件。
 *
 * 使用 Redis Stream 替代 pub/sub 方式，实现更可靠的消息传递
 * （Stream 会持久化消息，pub/sub 不会）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteRefresher implements CommandLineRunner {

    private final StringRedisTemplate redisTemplate;
    private final RouteCacheHolder routeCacheHolder;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        loadRoutesFromRedis();
        startStreamListener();
    }

    /**
     * Load all route definitions from Redis Hash on startup.
     */
    private void loadRoutesFromRedis() {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(CacheKeyRegistry.GATEWAY_ROUTES.getPrefix());
            if (entries.isEmpty()) {
                log.info("No route definitions found in Redis");
                return;
            }

            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                try {
                    RouteDefinition definition = objectMapper.readValue(
                            entry.getValue().toString(), RouteDefinition.class);
                    routeCacheHolder.put(definition);
                } catch (Exception e) {
                    log.error("Failed to parse route definition for key: {}", entry.getKey(), e);
                }
            }

            log.info("Loaded {} route definitions from Redis", entries.size());
        } catch (Exception e) {
            log.error("Failed to load route definitions from Redis", e);
        }
    }

    /**
     * Listen to Redis Stream for route change events.
     * When a route is added/updated/deleted in the SYSTEM module,
     * it sends a message to the stream, which triggers a route cache refresh.
     */
    private void startStreamListener() {
        // Stream listener runs asynchronously
        Thread listener = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    var messages = redisTemplate.opsForStream().read(
                            org.springframework.data.redis.connection.stream.Consumer.from(
                                    CacheKeyRegistry.GATEWAY_ROUTE_STREAM.getPrefix(), "gateway-consumer"),
                            org.springframework.data.redis.connection.stream.StreamOffset.create(
                                    CacheKeyRegistry.GATEWAY_ROUTE_STREAM.getPrefix(),
                                    org.springframework.data.redis.connection.stream.ReadOffset.lastConsumed())
                    );

                    if (messages != null) {
                        for (var message : messages) {
                            String action = (String) message.getValue().get("action");
                            log.info("Received route change event: {}", action);
                            loadRoutesFromRedis();
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Stream listener error", e);
            }
        }, "route-stream-listener");
        listener.setDaemon(true);
        listener.start();

        log.info("Route stream listener started on: {}", CacheKeyRegistry.GATEWAY_ROUTE_STREAM.getPrefix());
    }
}