package com.smart.common.gateway.route;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for dynamic route definitions.
 * Route definitions are loaded from Redis on startup and refreshed via Redis Stream events.
 *
 * 动态路由定义的内存缓存。
 * 路由定义在启动时从 Redis 加载，并通过 Redis Stream 事件刷新。
 */
@Slf4j
@Component
public class RouteCacheHolder {

    private final Map<String, RouteDefinition> routeDefinitions = new ConcurrentHashMap<>();

    public void put(RouteDefinition definition) {
        routeDefinitions.put(definition.getId(), definition);
        log.debug("Cached route definition: {}", definition.getId());
    }

    public void remove(String routeId) {
        routeDefinitions.remove(routeId);
        log.debug("Removed route definition: {}", routeId);
    }

    public List<RouteDefinition> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(routeDefinitions.values()));
    }

    public void clear() {
        routeDefinitions.clear();
    }

    public boolean isEmpty() {
        return routeDefinitions.isEmpty();
    }
}