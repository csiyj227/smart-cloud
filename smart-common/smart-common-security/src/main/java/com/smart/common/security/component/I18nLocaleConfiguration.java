package com.smart.common.security.component;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Servlet-based locale configuration for i18n.
 * <p>
 * Uses a {@link BeanPostProcessor} to customize the auto-configured {@code localeResolver}
 * created by Spring MVC, avoiding bean name conflicts.
 * <p>
 * Resolves locale from the {@code Accept-Language} header, falling back to {@code zh_CN}.
 * Also supports an explicit {@code lang} query parameter, e.g. {@code ?lang=en}.
 *
 * 基于 Servlet 的国际化区域配置。
 * <p>
 * 使用 {@link BeanPostProcessor} 自定义 Spring MVC 自动配置的 {@code localeResolver}，避免 Bean 名称冲突。
 * <p>
 * 从 {@code Accept-Language} 请求头解析区域设置，默认回退为 {@code zh_CN}，
 * 同时支持通过 {@code lang} 查询参数显式指定，例如 {@code ?lang=en}。
 */
@Configuration(proxyBeanMethods = false)
public class I18nLocaleConfiguration {

    /**
     * Post-processes the auto-configured {@code localeResolver} bean to apply
     * custom default locale and supported locales, and to support {@code ?lang=} parameter.
     */
    @Bean
    public static BeanPostProcessor i18nLocaleResolverCustomizer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("localeResolver".equals(beanName) && bean instanceof LocaleResolver) {
                    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver() {
                        @Override
                        public Locale resolveLocale(HttpServletRequest request) {
                            String langParam = request.getParameter("lang");
                            if (langParam != null && !langParam.isBlank()) {
                                return Locale.forLanguageTag(langParam.replace("_", "-"));
                            }
                            return super.resolveLocale(request);
                        }
                    };
                    resolver.setDefaultLocale(Locale.CHINA);
                    resolver.setSupportedLocales(List.of(Locale.CHINA, Locale.ENGLISH));
                    return resolver;
                }
                return bean;
            }
        };
    }
}
