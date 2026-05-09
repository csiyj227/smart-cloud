package com.smart.flow.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a dedicated {@link ObjectMapper} used solely by the FlowChart compiler and the
 * AssigneeResolver SPI. Keeping a separate mapper insulates the workflow module from any
 * application-wide Jackson tweaks (date format, naming strategy, etc.) that could otherwise
 * silently corrupt the persisted DSL JSON. The bean is exposed under the explicit name
 * {@code flowObjectMapper} so callers must qualify their injection - this prevents the
 * primary mapper auto-configured by Spring Boot from being silently substituted.
 */
@Configuration
public class FlowJacksonConfig {

    public static final String BEAN_NAME = "flowObjectMapper";

    @Bean(BEAN_NAME)
    public ObjectMapper flowObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
}
