package com.smart.common.core.spring;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Provides static access to the Spring {@link ApplicationContext}
 * for classes not managed by the container.
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext ctx;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        ctx = applicationContext;
    }

    public static ApplicationContext context() {
        return ctx;
    }

    public static <T> T getBean(Class<T> type) {
        return ctx.getBean(type);
    }

    public static <T> T getBean(String name, Class<T> type) {
        return ctx.getBean(name, type);
    }

    public static <T> Map<String, T> getBeansOfType(Class<T> type) {
        return ctx.getBeansOfType(type);
    }

    public static void publishEvent(ApplicationEvent event) {
        ctx.publishEvent(event);
    }

    public static void publishEvent(Object event) {
        ctx.publishEvent(event);
    }

    public static String property(String key) {
        return ctx.getEnvironment().getProperty(key);
    }

    public static String property(String key, String fallback) {
        return ctx.getEnvironment().getProperty(key, fallback);
    }

    public static String activeProfile() {
        String[] profiles = ctx.getEnvironment().getActiveProfiles();
        return profiles.length > 0 ? profiles[0] : "";
    }
}
