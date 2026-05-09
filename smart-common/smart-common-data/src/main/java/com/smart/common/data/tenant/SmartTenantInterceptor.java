package com.smart.common.data.tenant;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.core.tenant.TenantContext;
import com.smart.common.data.config.SmartTenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Set;

/**
 * MyBatis-Plus tenant interceptor handler.
 * Determines which tables need tenant filtering and appends the tenant WHERE clause.
 *
 * A table is considered tenant-scoped if:
 * 1. Its entity class is annotated with @TenantEntity, OR
 * 2. Its name is listed in the smart.tenant.tables configuration property.
 *
 * Tenant filtering is skipped when:
 * - TenantContext.isBypassed() is true (super-admin mode)
 * - TenantContext.get() is null (no tenant context)
 *
 * MyBatis-Plus 租户拦截器处理器。
 * 确定哪些表需要租户过滤，并追加租户 WHERE 子句。
 *
 * 表被视为租户范围的条件：
 * 1. 其实体类标注了 @TenantEntity 注解，或
 * 2. 其表名在 smart.tenant.tables 配置属性中列出。
 *
 * 租户过滤在以下情况下跳过：
 * - TenantContextHolder.isTenantSkipped() 为 true（超级管理员模式）
 * - TenantContextHolder.getTenantId() 为 null（无租户上下文）
 */
@Slf4j
@RequiredArgsConstructor
public class SmartTenantInterceptor implements com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler {

    private final SmartTenantProperties properties;

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.get().orElse(null);
        if (tenantId == null) {
            return new LongValue(0);
        }
        return new LongValue(tenantId);
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // Skip if tenant context is marked to skip (super-admin)
        if (TenantContext.isBypassed()) {
            return true;
        }

        // Skip if no tenant ID in context
        if (TenantContext.get().orElse(null) == null) {
            return true;
        }

        // Check if table is in the configured tenant table list
        Set<String> tenantTables = properties.getTables();
        if (tenantTables != null && tenantTables.contains(tableName)) {
            return false;
        }

        // Check if the entity class has @TenantEntity annotation
        return !hasTenantEntityAnnotation(tableName);
    }

    @Override
    public String getTenantIdColumn() {
        return properties.getColumn();
    }

    /**
     * Check if the entity mapped to this table has @TenantEntity annotation.
     */
    private boolean hasTenantEntityAnnotation(String tableName) {
        try {
            var tableInfo = TableInfoHelper.getTableInfo(tableName);
            if (tableInfo == null) {
                return false;
            }
            Class<?> entityClass = tableInfo.getEntityType();
            return entityClass != null && entityClass.isAnnotationPresent(TenantEntity.class);
        } catch (Exception e) {
            log.trace("Could not check @TenantEntity for table: {}", tableName, e);
            return false;
        }
    }
}