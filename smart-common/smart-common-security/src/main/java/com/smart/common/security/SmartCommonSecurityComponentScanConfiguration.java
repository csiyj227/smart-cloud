package com.smart.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Smart公共安全模块的组件扫描自动配置类。
 * 通过 @ComponentScan 自动扫描 com.smart.common.security 包下的所有组件，
 * 使各微服务引入安全模块依赖后无需手动配置即可加载安全相关Bean。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.smart.common.security")
public class SmartCommonSecurityComponentScanConfiguration {
}
