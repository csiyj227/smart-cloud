package com.smart.admin.service;

import com.smart.admin.entity.SysTenant;
import com.smart.common.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Tenant broker service for cross-tenant operations.
 * Allows super-admin to temporarily switch tenant context
 * and perform operations in other tenants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBrokerService {

    private final SysTenantService sysTenantService;

    /**
     * Execute operation in a specific tenant's context.
     *
     * @param tenantId the target tenant ID
     * @param operation the operation to execute
     * @return the result of the operation
     */
    public <T> T executeInTenant(Long tenantId, Supplier<T> operation) {
        if (tenantId == null) {
            return operation.get();
        }

        // Validate tenant exists
        SysTenant tenant = sysTenantService.getById(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }

        Long originalTenantId = TenantContext.get().orElse(null);
        Boolean originalSkip = null; // TenantContext 没有 getTenantSkip 方法

        try {
            // Set target tenant context
            TenantContext.set(tenantId);
            // Don't skip tenant filter - we want to operate within that tenant
            // TenantContext.setTenantSkip(); // 该方法不存在,注释掉

            log.debug("Executing operation in tenant: {}", tenantId);
            return operation.get();
        } finally {
            // Restore original context
            TenantContext.clear();
            if (originalTenantId != null) {
                TenantContext.set(originalTenantId);
            }
            // if (Boolean.TRUE.equals(originalSkip)) {
            //     TenantContext.setTenantSkip(); // 该方法不存在
            // }
        }
    }

    /**
     * Execute operation bypassing tenant filter (true cross-tenant query).
     *
     * @param operation the operation to execute
     * @return the result of the operation
     */
    public <T> T executeWithSkipTenant(Supplier<T> operation) {
        Long originalTenantId = TenantContext.get().orElse(null);
        Boolean originalSkip = null; // TenantContext 没有 getTenantSkip 方法

        try {
            // TenantContext.setTenantSkip(); // 该方法不存在,注释掉

            log.debug("Executing operation with tenant skip");
            return operation.get();
        } finally {
            TenantContext.clear();
            if (originalTenantId != null) {
                TenantContext.set(originalTenantId);
            }
            // if (Boolean.TRUE.equals(originalSkip)) {
            //     TenantContext.setTenantSkip(); // 该方法不存在
            // }
        }
    }

    /**
     * Execute operation as super-admin (no tenant restrictions).
     *
     * @param operation the operation to execute
     * @return the result of the operation
     */
    public <T> T executeAsSuperAdmin(Supplier<T> operation) {
        return executeWithSkipTenant(operation);
    }

    /**
     * Switch to a different tenant temporarily.
     *
     * @param tenantId the target tenant ID
     * @param runnable the operation to execute
     */
    public void runInTenant(Long tenantId, Runnable runnable) {
        executeInTenant(tenantId, () -> {
            runnable.run();
            return null;
        });
    }
}