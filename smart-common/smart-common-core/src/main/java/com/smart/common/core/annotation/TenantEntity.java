package com.smart.common.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a MyBatis-Plus entity class as tenant-isolated.
 * The tenant interceptor will automatically append WHERE tenant_id = ? to all queries
 * on tables mapped by annotated entities.
 *
 * Alternatively, tenant-isolated tables can be listed in the configuration property
 * smart.tenant.tables.
 *
 * 标记 MyBatis-Plus 实体类为租户隔离。
 * 租户拦截器会自动为被注解实体映射的表的所有查询追加 WHERE tenant_id = ? 条件。
 * 也可以通过配置属性 smart.tenant.tables 来指定租户隔离的表。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface TenantEntity {
}