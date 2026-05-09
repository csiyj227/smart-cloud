package com.smart.gateway.filter;

import com.smart.common.core.auth.AuthHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Core global filter for the Smart gateway.
 *
 * Responsibilities:
 * 1. Strip the X-Internal-Call header from all incoming requests (security)
 *    to prevent external callers from spoofing internal service calls.
 * 2. Apply global StripPrefix=1 so /admin/user/list becomes /user/list for the target service.
 * 3. Record request start time for latency tracking.
 *
 * Smart 网关核心全局过滤器。
 *
 * 职责：
 * 1. 从所有外部请求中移除 X-Internal-Call 请求头（安全边界），防止外部调用者伪造内部服务调用。
 * 2. 应用全局 StripPrefix=1，使 /admin/user/list 变为 /user/list 发送到目标服务。
 * 3. 记录请求开始时间，用于延迟追踪。
 */
@Slf4j
@Component
public class SmartRequestGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Strip internal call header from external requests (security boundary)
        ServerHttpRequest.Builder builder = request.mutate();
        builder.headers(headers -> {
            headers.remove(AuthHeaders.SERVICE_CALL);
        });

        // Generate or propagate traceId for full-link tracing
        String traceId = request.getHeaders().getFirst(AuthHeaders.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        builder.header(AuthHeaders.TRACE_ID, traceId);

        // Record request start time
        exchange.getAttributes().put("requestStartTime", System.currentTimeMillis());

        ServerHttpRequest newRequest = builder.build();
        return chain.filter(exchange.mutate().request(newRequest).build())
                .doFinally(signalType -> {
                    Long startTime = exchange.getAttribute("requestStartTime");
                    if (startTime != null) {
                        long duration = System.currentTimeMillis() - startTime;
                        String path = request.getURI().getPath();
                        log.debug("Gateway request: {} {} - {}ms",
                                request.getMethod(), path, duration);
                    }
                });
    }

    @Override
    public int getOrder() {
        return 10;
    }
}