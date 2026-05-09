package com.smart.common.core.tenant;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thread-local context for the current tenant.
 *
 * <p>Supports three usage patterns:
 * <ol>
 *   <li>Manual: {@code TenantContext.set(1L); ... TenantContext.clear();}</li>
 *   <li>Scoped: {@code try (var scope = TenantContext.scope(1L)) { ... }}</li>
 *   <li>Functional: {@code TenantContext.runAs(1L, () -> doWork());}</li>
 * </ol>
 *
 * <p>Uses {@link TransmittableThreadLocal} so the value propagates
 * across thread-pool submissions and async callbacks.
 */
public final class TenantContext {

    private TenantContext() {
    }

    private static final TransmittableThreadLocal<Long> CURRENT_TENANT = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<Boolean> BYPASS_FLAG = new TransmittableThreadLocal<>();

    // ── basic accessors ────────────────────────────────────────

    public static void set(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Optional<Long> get() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static Long require() {
        Long id = CURRENT_TENANT.get();
        if (id == null) {
            throw new IllegalStateException("Tenant context is not initialized");
        }
        return id;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        BYPASS_FLAG.remove();
    }

    // ── bypass (super-admin cross-tenant access) ──────────────

    public static void enableBypass() {
        BYPASS_FLAG.set(Boolean.TRUE);
    }

    public static boolean isBypassed() {
        return Boolean.TRUE.equals(BYPASS_FLAG.get());
    }

    // ── scoped context (try-with-resources) ───────────────────

    public static Scope scope(Long tenantId) {
        Long previous = CURRENT_TENANT.get();
        CURRENT_TENANT.set(tenantId);
        return () -> {
            if (previous == null) {
                CURRENT_TENANT.remove();
            } else {
                CURRENT_TENANT.set(previous);
            }
        };
    }

    /** AutoCloseable scope that restores the previous tenant on close. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close(); // no checked exception
    }

    // ── functional API ────────────────────────────────────────

    public static void runAs(Long tenantId, Runnable action) {
        try (Scope ignored = scope(tenantId)) {
            action.run();
        }
    }

    public static <T> T supplyAs(Long tenantId, Supplier<T> action) {
        try (Scope ignored = scope(tenantId)) {
            return action.get();
        }
    }

    public static void runWithoutTenant(Runnable action) {
        Long previous = CURRENT_TENANT.get();
        try {
            CURRENT_TENANT.remove();
            enableBypass();
            action.run();
        } finally {
            BYPASS_FLAG.remove();
            if (previous != null) {
                CURRENT_TENANT.set(previous);
            }
        }
    }
}
