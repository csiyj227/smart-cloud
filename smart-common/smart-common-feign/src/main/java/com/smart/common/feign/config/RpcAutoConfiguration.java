package com.smart.common.feign.config;

import com.smart.common.feign.interceptor.ContextPropagateInterceptor;
import com.smart.common.feign.interceptor.ServiceCallInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for inter-service RPC interceptors.
 * Registered via META-INF/spring/AutoConfiguration.imports.
 */
@AutoConfiguration
public class RpcAutoConfiguration {

    @Bean
    public ServiceCallInterceptor serviceCallInterceptor() {
        return new ServiceCallInterceptor();
    }

    @Bean
    public ContextPropagateInterceptor contextPropagateInterceptor() {
        return new ContextPropagateInterceptor();
    }
}
