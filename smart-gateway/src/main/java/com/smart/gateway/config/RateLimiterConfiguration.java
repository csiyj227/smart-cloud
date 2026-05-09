package com.smart.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Redis-based rate limiter configuration for the gateway.
 * Uses client IP address as the rate limiting key.
 *
 * 网关基于 Redis 的限流配置。
 * 使用客户端 IP 地址作为限流键。
 */
@Configuration
public class RateLimiterConfiguration {

    @Bean
    public KeyResolver remoteAddrKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (ip == null || ip.isEmpty()) {
                ip = exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown";
            }
            return Mono.just(ip);
        };
    }
}