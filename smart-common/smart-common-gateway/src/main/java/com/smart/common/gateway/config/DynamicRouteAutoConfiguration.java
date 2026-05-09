package com.smart.common.gateway.config;

import com.smart.common.gateway.canary.CanaryLoadBalancer;
import com.smart.common.gateway.route.RedisRouteDefinitionRepository;
import com.smart.common.gateway.route.RouteCacheHolder;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for dynamic route management and canary load balancing.
 *
 * 动态路由管理和金丝雀负载均衡的自动配置。
 */
@Configuration
public class DynamicRouteAutoConfiguration {

    @Bean
    public RouteDefinitionRepository routeDefinitionRepository(RouteCacheHolder routeCacheHolder) {
        return new RedisRouteDefinitionRepository(routeCacheHolder);
    }

    @Bean
    public CanaryLoadBalancer canaryLoadBalancer(ServiceInstanceListSupplier supplier, String serviceId) {
        return new CanaryLoadBalancer(supplier, serviceId);
    }
}