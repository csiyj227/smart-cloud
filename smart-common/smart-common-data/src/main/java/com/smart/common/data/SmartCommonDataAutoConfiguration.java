package com.smart.common.data;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Smart公共数据模块自动配置类。
 * 通过 @ComponentScan 扫描 com.smart.common.data 包下的所有组件。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.smart.common.data")
public class SmartCommonDataAutoConfiguration {
}
