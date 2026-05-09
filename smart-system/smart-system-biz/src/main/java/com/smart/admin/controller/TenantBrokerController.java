package com.smart.admin.controller;

import com.smart.admin.entity.SysTenant;
import com.smart.admin.service.SysTenantService;
import com.smart.admin.service.TenantBrokerService;
import com.smart.admin.support.JwtClaimUtils;
import com.smart.common.core.tenant.TenantContext;
import com.smart.common.core.web.ApiResult;
import com.smart.common.data.tenant.TenantSwitchStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 租户切换控制器。
 *
 * <p><b>关键设计：</b> 切换状态通过 {@link TenantSwitchStore} 持久化到 Redis（按用户维度），
 * 而非依赖 ThreadLocal。后续所有请求经过 {@code TenantContextFilter} 时会优先
 * 读取 Redis 中的 override 替代 JWT/header 中的默认租户，实现真正跨请求的"切换"。
 *
 * <p>之前版本错误地直接调 {@code TenantContext.setTenantId}，由于 ThreadLocal
 * 在请求结束就被 Filter 的 {@code finally { clear() }} 清空，导致：
 * <pre>
 *   POST /switch/0  →  ok          // 当前请求 ThreadLocal=0，请求结束被清空
 *   GET  /current   →  data: 1     // 新请求新线程，ThreadLocal=null，又被 header=1 覆盖
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/system/tenant-broker")
@RequiredArgsConstructor
public class TenantBrokerController {

    private final TenantBrokerService tenantBrokerService;
    private final SysTenantService sysTenantService;
    private final TenantSwitchStore tenantSwitchStore;

    @PreAuthorize("@authz.hasPermission('sys_tenant_view')")
    @GetMapping("/tenants")
    public ApiResult<List<SysTenant>> getAllTenants() {
        List<SysTenant> tenants = tenantBrokerService.executeWithSkipTenant(sysTenantService::list);
        return ApiResult.success(tenants);
    }

    /**
     * 切换到指定租户。把 override 租户写到 Redis（按当前用户 ID），
     * 后续所有请求都会以此租户作为有效租户，直到调用 {@code /exit} 或 30 分钟过期。
     */
    @PreAuthorize("@authz.hasPermission('sys_tenant_switch')")
    @PostMapping("/switch/{tenantId}")
    public ApiResult<Void> switchTenant(@PathVariable Long tenantId, Authentication authentication) {
        // 校验目标租户存在 — 用 skipTenant 避免被当前 tenant 过滤器挡住
        SysTenant tenant = tenantBrokerService.executeWithSkipTenant(() -> sysTenantService.getById(tenantId));
        if (tenant == null) {
            return ApiResult.failure("Tenant not found");
        }

        Long currentTenantId = JwtClaimUtils.getLong(authentication, "tenant_id");
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        String currentUsername = JwtClaimUtils.getString(authentication, "username", authentication.getName());
        if (userId == null) {
            return ApiResult.failure("Cannot resolve current user id");
        }
        if (!Objects.equals(currentTenantId, tenantId) && !hasSuperAdminRole(authentication)) {
            return ApiResult.failure("No permission to switch to this tenant");
        }

        // 把"用户当前激活的租户"持久化到 Redis（跨请求生效）
        tenantSwitchStore.setOverride(userId, tenantId);

        // 当前请求也立即生效，后续 service 调用读到正确租户
        TenantContext.set(tenantId);

        log.info("User {} (id={}) switched to tenant {}", currentUsername, userId, tenantId);
        return ApiResult.success();
    }

    /**
     * 退出切换，恢复到 JWT 中的默认租户。
     */
    @PostMapping("/exit")
    public ApiResult<Void> exitSwitch(Authentication authentication) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        if (userId != null) {
            tenantSwitchStore.clearOverride(userId);
        }
        // 当前请求也立即恢复
        Long defaultTenantId = JwtClaimUtils.getLong(authentication, "tenant_id");
        if (defaultTenantId != null) {
            TenantContext.set(defaultTenantId);
        } else {
            TenantContext.clear();
        }
        return ApiResult.success();
    }

    /**
     * 返回当前生效的租户 ID。
     * 优先级：Redis override > JWT 默认租户。
     */
    @GetMapping("/current")
    public ApiResult<Long> getCurrentTenant(Authentication authentication) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        if (userId != null) {
            Long override = tenantSwitchStore.getOverride(userId);
            if (override != null) {
                return ApiResult.success(override);
            }
        }
        // 没有切换 → 返回 JWT 中的默认租户
        return ApiResult.success(JwtClaimUtils.getLong(authentication, "tenant_id"));
    }

    /**
     * 当前用户是否处于"切换状态"（有 Redis override 且与默认租户不同）。
     */
    @GetMapping("/switched")
    public ApiResult<Boolean> isSwitched(Authentication authentication) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        if (userId == null) {
            return ApiResult.success(false);
        }
        Long override = tenantSwitchStore.getOverride(userId);
        Long defaultTenantId = JwtClaimUtils.getLong(authentication, "tenant_id");
        return ApiResult.success(override != null && !Objects.equals(override, defaultTenantId));
    }

    private boolean hasSuperAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") || a.getAuthority().equals("ROLE_ADMIN"));
    }
}
