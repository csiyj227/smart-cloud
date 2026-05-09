package com.smart.common.data.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.smart.common.data.config.SmartTenantProperties;
import com.smart.common.data.datascope.DataPermissionInterceptor;
import com.smart.common.data.datascope.DataPermissionResolver;
import com.smart.common.data.tenant.SmartTenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/**
 * MyBatis-Plus configuration with interceptor chain:
 * 1. Pagination interceptor (PostgreSQL dialect)
 * 2. Optimistic lock interceptor
 * 3. Tenant interceptor (tenant_id filtering)
 * 4. Data permission interceptor (row-level dept/user filtering)
 *
 * MyBatis-Plus 配置类，配置拦截器链：
 * 1. 分页拦截器（PostgreSQL 方言）
 * 2. 乐观锁拦截器
 * 3. 租户拦截器（tenant_id 过滤）
 * 4. 数据权限拦截器（行级部门/用户过滤）
 */
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(SmartTenantProperties.class)
public class MybatisPlusConfiguration {

    private final SmartTenantProperties tenantProperties;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Lazy ObjectProvider<DataPermissionResolver> permissionResolverProvider) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. Pagination interceptor (PostgreSQL)
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        paginationInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(paginationInterceptor);

        // 2. Optimistic lock interceptor
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 3. Tenant interceptor (if enabled)
        if (tenantProperties.isEnabled()) {
            TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor(
                    new SmartTenantInterceptor(tenantProperties)
            );
            interceptor.addInnerInterceptor(tenantInterceptor);
        }

        // 4. Data permission interceptor (only if resolver is available)
        DataPermissionInterceptor dataPermissionInterceptor = new DataPermissionInterceptor(permissionResolverProvider);
        com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor dpInner =
                new com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor(dataPermissionInterceptor);
        interceptor.addInnerInterceptor(dpInner);

        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public AutoFillMetaObjectHandler autoFillMetaObjectHandler() {
        return new AutoFillMetaObjectHandler();
    }
}