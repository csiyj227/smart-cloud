package com.smart.admin.job.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.AdaptableJobFactory;

/**
 * Quartz 调度器配置。
 *
 * <p>核心目的：让 Quartz 实例化 Job 时走 Spring 容器（自动装配 @Autowired 字段），
 * 默认实现 {@link AdaptableJobFactory} 是直接 newInstance 的，无法注入依赖。
 *
 * <p>JobStore (JDBC) / 集群相关参数全部由 application.yml 中的 {@code spring.quartz.*} 提供，
 * Spring Boot 自带的 QuartzAutoConfiguration 会负责创建 SchedulerFactoryBean。
 */
@Configuration
public class QuartzConfig {

    @Bean
    public AutowireCapableJobFactory autowireCapableJobFactory() {
        return new AutowireCapableJobFactory();
    }

    /** 让 Quartz 创建 Job 时走 Spring 自动装配，使任务类可以 @Autowired Service */
    public static class AutowireCapableJobFactory extends AdaptableJobFactory implements ApplicationContextAware {

        private AutowireCapableBeanFactory beanFactory;

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
            Object jobInstance = super.createJobInstance(bundle);
            beanFactory.autowireBean(jobInstance);
            return jobInstance;
        }
    }
}
