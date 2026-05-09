package com.smart.common.data.tenant;

import com.smart.common.core.tenant.TenantContext;

import lombok.experimental.UtilityClass;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Safe tenant context switching utility.
 * Saves the current tenant context, switches to the target tenant,
 * executes the action, then restores the original context in a finally block.
 * This prevents tenant context leakage in nested calls.
 *
 * 安全的租户上下文切换工具类。
 * 保存当前租户上下文，切换到目标租户执行操作，
 * 然后在 finally 块中恢复原始上下文，防止嵌套调用中的租户上下文泄漏。
 */
@UtilityClass
public class TenantScope {

    /**
     * Run an action as a specific tenant, restoring the original context afterward.
     */
    public void runAs(Long tenantId, Runnable action) {
        Long previousId = TenantContext.get().orElse(null);
        Boolean previousSkip = TenantContext.isBypassed();
        try {
            TenantContext.clear();
            TenantContext.set(tenantId);
            action.run();
        } finally {
            TenantContext.clear();
            restore(previousId, previousSkip);
        }
    }

    /**
     * Run an action as a specific tenant (lazy-resolved), restoring context afterward.
     */
    public void runAs(Supplier<Long> tenantSupplier, Runnable action) {
        runAs(tenantSupplier.get(), action);
    }

    /**
     * Execute a function as a specific tenant and return the result.
     */
    public <T> T applyAs(Long tenantId, Supplier<T> action) {
        Long previousId = TenantContext.get().orElse(null);
        Boolean previousSkip = TenantContext.isBypassed();
        try {
            TenantContext.clear();
            TenantContext.set(tenantId);
            return action.get();
        } finally {
            TenantContext.clear();
            restore(previousId, previousSkip);
        }
    }

    /**
     * Execute a function as a specific tenant (lazy-resolved) and return the result.
     */
    public <T> T applyAs(Supplier<Long> tenantSupplier, Supplier<T> action) {
        return applyAs(tenantSupplier.get(), action);
    }

    /**
     * Run an action with tenant filtering completely disabled.
     * Use with caution - only for super-admin cross-tenant operations.
     */
    public void runWithoutTenant(Runnable action) {
        Long previousId = TenantContext.get().orElse(null);
        Boolean previousSkip = TenantContext.isBypassed();
        try {
            TenantContext.clear();
            TenantContext.enableBypass();
            action.run();
        } finally {
            TenantContext.clear();
            restore(previousId, previousSkip);
        }
    }

    /**
     * Execute a function with tenant filtering disabled and return the result.
     */
    public <T> T applyWithoutTenant(Supplier<T> action) {
        Long previousId = TenantContext.get().orElse(null);
        Boolean previousSkip = TenantContext.isBypassed();
        try {
            TenantContext.clear();
            TenantContext.enableBypass();
            return action.get();
        } finally {
            TenantContext.clear();
            restore(previousId, previousSkip);
        }
    }

    private void restore(Long tenantId, Boolean skip) {
        if (tenantId != null) {
            TenantContext.set(tenantId);
        }
        if (Boolean.TRUE.equals(skip)) {
            TenantContext.enableBypass();
        }
    }
}