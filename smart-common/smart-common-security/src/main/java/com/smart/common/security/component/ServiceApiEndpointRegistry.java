package com.smart.common.security.component;

import com.smart.common.security.annotation.ServiceApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Auto-discovers all @ServiceApi-annotated endpoints at startup
 * and adds them to the Spring Security permit-all list.
 * This avoids the need to manually configure each service API endpoint.
 *
 * 在应用启动时自动发现所有标注了 @ServiceApi 注解的端点，
 * 并将其添加到 Spring Security 的放行列表中，
 * 从而避免手动配置每个服务 API 端点。
 */
@Slf4j
public class ServiceApiEndpointRegistry {

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public ServiceApiEndpointRegistry(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    /**
     * Collect all URL patterns for @ServiceApi-annotated endpoints.
     */
    public Set<String> getServiceApiEndpoints() {
        Set<String> endpoints = new HashSet<>();
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            ServiceApi serviceApi = handlerMethod.getMethodAnnotation(ServiceApi.class);
            if (serviceApi == null) {
                serviceApi = handlerMethod.getBeanType().getAnnotation(ServiceApi.class);
            }

            if (serviceApi != null) {
                RequestMappingInfo mappingInfo = entry.getKey();
                Set<String> patterns = mappingInfo.getPatternValues();
                endpoints.addAll(patterns);
                log.debug("Registered @ServiceApi endpoint: {}", patterns);
            }
        }

        log.info("Discovered {} @ServiceApi endpoints", endpoints.size());
        return endpoints;
    }
}
