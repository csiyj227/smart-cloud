package com.smart.common.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Tenant configuration properties.
 *
 * 租户配置属性。
 */
@Data
@ConfigurationProperties(prefix = "smart.tenant")
public class SmartTenantProperties {

    /**
     * Whether tenant filtering is enabled. Default: true
     */
    private boolean enabled = true;

    /**
     * The column name used for tenant isolation in database tables.
     * Default: tenant_id
     */
    private String column = "tenant_id";

    /**
     * Set of table names that should have tenant filtering applied.
     * Tables whose entity classes are annotated with @TenantEntity are
     * automatically included; this list is for tables without entity annotations.
     */
    private Set<String> tables;

    /**
     * List of tenant IDs that are considered super-admin tenants
     * and can bypass tenant filtering when explicitly requested.
     */
    private Set<Long> superAdminTenantIds;
}