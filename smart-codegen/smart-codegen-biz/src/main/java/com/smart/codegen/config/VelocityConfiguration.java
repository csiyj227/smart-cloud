package com.smart.codegen.config;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Velocity 引擎配置。
 *
 * <p>同时启用：
 * <ul>
 *   <li>classpath loader：用于加载 {@code resources/templates/*.vm} 内置模板</li>
 *   <li>string loader (默认 evaluate)：用于动态渲染 DB 中存储的模板字符串和路径表达式</li>
 * </ul>
 */
@Configuration
public class VelocityConfiguration {

    @Bean
    public VelocityEngine velocityEngine() {
        Properties props = new Properties();
        // classpath 资源加载器：用于读取 templates/*.vm
        props.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        props.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        props.setProperty(RuntimeConstants.INPUT_ENCODING, "UTF-8");
        // 静默 Velocity 默认 log，避免污染 Spring 日志
        props.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogChute");
        VelocityEngine engine = new VelocityEngine(props);
        engine.init();
        return engine;
    }
}