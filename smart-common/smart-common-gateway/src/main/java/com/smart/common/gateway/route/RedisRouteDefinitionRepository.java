package com.smart.common.gateway.route;

import com.smart.common.core.cache.CacheKeyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Redis-backed route definition repository.
 * Reads route definitions from the RouteCacheHolder which is populated
 * from Redis on startup and refreshed via Redis Stream events.
 *
 * 基于 Redis 的路由定义仓库。
 * 从 RouteCacheHolder 读取路由定义，该缓存在启动时从 Redis 加载，
 * 并通过 Redis Stream 事件进行刷新。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {

    private final RouteCacheHolder routeCacheHolder;

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(routeCacheHolder.getAll());
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.doOnNext(definition -> {
            routeCacheHolder.put(definition);
            log.info("Saved route definition: {}", definition.getId());
        }).then();
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.doOnNext(id -> {
            routeCacheHolder.remove(id);
            log.info("Deleted route definition: {}", id);
        }).then();
    }
}