package com.smart.common.swagger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Swagger/OpenAPI auto-configuration for Smart services.
 *
 * Smart 服务的 Swagger/OpenAPI 自动配置。
 */
@AutoConfiguration
public class SmartSwaggerConfiguration {

    @Bean
    public OpenAPI smartOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Smart API Documentation")
                        .description("Smart Microservice Scaffold API")
                        .version("1.0.0")
                        .license(new License().name("Apache 2.0")));
    }
}