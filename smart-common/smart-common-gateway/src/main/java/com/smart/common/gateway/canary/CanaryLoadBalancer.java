package com.smart.common.gateway.canary;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Canary (gray) release load balancer.
 * Routes requests to specific service instances based on the "version" request header.
 *
 * Logic:
 * 1. If request has a "version" header, route to instances with matching Nacos metadata "version".
 * 2. If no version header or no matching instances, route to instances WITHOUT a "version" metadata tag.
 * 3. Fall back to round-robin among all instances if no better match.
 *
 * 金丝雀（灰度）发布负载均衡器。
 * 根据请求头 "version" 将请求路由到特定版本的服务实例。
 *
 * 逻辑：
 * 1. 如果请求携带 "version" 请求头，路由到 Nacos 元数据中 version 匹配的实例。
 * 2. 如果没有 version 请求头或没有匹配的实例，路由到没有 "version" 元数据标签的实例（稳定版）。
 * 3. 如果仍无匹配，回退到所有实例的轮询选择。
 */
@Slf4j
public class CanaryLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final ServiceInstanceListSupplier supplier;
    private final String serviceId;
    private final AtomicInteger counter = new AtomicInteger(new Random().nextInt(1000));

    public CanaryLoadBalancer(ServiceInstanceListSupplier supplier, String serviceId) {
        this.supplier = supplier;
        this.serviceId = serviceId;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        String targetVersion = null;
        if (request.getContext() instanceof org.springframework.http.HttpRequest httpRequest) {
            targetVersion = httpRequest.getHeaders().getFirst("version");
        }

        final String version = targetVersion;
        return supplier.get().next().map(instances -> selectInstance(instances, version));
    }

    @Override
    public Mono<Response<ServiceInstance>> choose() {
        return supplier.get().next().map(instances -> selectInstance(instances, null));
    }

    private Response<ServiceInstance> selectInstance(List<ServiceInstance> instances, String targetVersion) {
        if (instances == null || instances.isEmpty()) {
            log.warn("No instances available for service: {}", serviceId);
            return new EmptyResponse();
        }

        // If version specified, try to match
        if (targetVersion != null && !targetVersion.isEmpty()) {
            List<ServiceInstance> matched = instances.stream()
                    .filter(inst -> targetVersion.equals(inst.getMetadata().get("version")))
                    .toList();

            if (!matched.isEmpty()) {
                ServiceInstance selected = matched.get(incrementAndGetModulo(matched.size()));
                log.debug("Canary route: service={}, version={}, instance={}",
                        serviceId, targetVersion, selected.getInstanceId());
                return new DefaultResponse(selected);
            }
        }

        // Default: route to instances without version metadata (stable instances)
        List<ServiceInstance> stable = instances.stream()
                .filter(inst -> !inst.getMetadata().containsKey("version"))
                .toList();

        if (!stable.isEmpty()) {
            return new DefaultResponse(stable.get(incrementAndGetModulo(stable.size())));
        }

        // Fallback: round-robin among all instances
        return new DefaultResponse(instances.get(incrementAndGetModulo(instances.size())));
    }

    private int incrementAndGetModulo(int modulo) {
        int current, next;
        do {
            current = counter.get();
            next = (current + 1) % modulo;
        } while (!counter.compareAndSet(current, next));
        return next;
    }
}