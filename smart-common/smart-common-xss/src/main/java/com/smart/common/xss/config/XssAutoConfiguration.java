package com.smart.common.xss.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.common.xss.filter.XssFilter;
import com.smart.common.xss.serializer.MaskFieldSerializer;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for XSS protection and field masking.
 *
 * XSS 防护和字段脱敏的自动配置。
 */
@AutoConfiguration
@ConditionalOnWebApplication
public class XssAutoConfiguration {

    @Bean
    public FilterRegistrationBean<Filter> xssFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new XssFilter());
        registration.addUrlPatterns("/*");
        registration.setName("xssFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}