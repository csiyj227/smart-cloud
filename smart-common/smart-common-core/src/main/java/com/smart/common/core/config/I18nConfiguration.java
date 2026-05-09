package com.smart.common.core.config;

import com.smart.common.core.util.I18nUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * I18n auto-configuration.
 * <p>
 * Registers a {@link MessageSource} that loads {@code classpath:i18n/messages_*.properties}.
 * <p>
 * The {@code LocaleResolver} bean (which depends on {@code spring-webmvc}) is registered
 * separately in {@code smart-common-security} where the webmvc dependency is available.
 *
 * 国际化自动配置。
 * <p>
 * 注册一个加载 {@code classpath:i18n/messages_*.properties} 的 {@link MessageSource}。
 * <p>
 * {@code LocaleResolver} Bean（依赖于 {@code spring-webmvc}）在 {@code smart-common-security} 中单独注册，
 * 因为那里有 webmvc 依赖可用。
 *
 * @see com.smart.common.core.util.I18nUtil
 */
@AutoConfiguration
public class I18nConfiguration {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        I18nUtil.setMessageSource(source);
        return source;
    }
}
