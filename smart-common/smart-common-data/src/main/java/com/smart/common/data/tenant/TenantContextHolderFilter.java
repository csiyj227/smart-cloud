package com.smart.common.data.tenant;

import com.smart.common.core.auth.AuthHeaders;
import com.smart.common.core.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that resolves the effective tenant ID for the current request
 * and sets it in {@link TenantContext}.
 *
 * <p>解析优先级（高 → 低）：
 * <ol>
 *   <li><b>Redis 中该用户的 override 租户</b>（来自 {@code TenantBrokerController.switch}）—
 *       超管/租户管理员通过"切换租户"功能临时进入其它租户时使用。</li>
 *   <li>请求 header {@code X-Tenant-Id} —— 网关在认证后注入的默认租户。</li>
 *   <li>当前 JWT 的 {@code tenant_id} claim —— boot 单体模式下没有网关时兜底。</li>
 * </ol>
 *
 * <p>之前版本只读 header → 导致 {@code TenantBrokerController.switch} 设置的
 * ThreadLocal 在请求结束就被 {@code finally { clear() }} 清空，下一次请求
 * 拿不到切换结果。新版本通过 Redis 跨请求持久化 override，根治该问题。
 */
/**
 * <p><b>Order 设计：</b> 必须排在 Spring Security 过滤链<b>之后</b>，
 * 否则 {@link SecurityContextHolder#getContext()} 还没被
 * {@code BearerTokenAuthenticationFilter} 填充，导致拿不到当前用户 → Redis override 永远失效。
 * Spring Security 默认 Order = -100，这里取 50 保证一定在其后执行。
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class TenantContextHolderFilter extends OncePerRequestFilter {

    private final TenantSwitchStore tenantSwitchStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Long tenantId = resolveTenantId(request);
            if (tenantId != null) {
                TenantContext.set(tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 按优先级解析当前请求的有效租户 ID。
     */
    private Long resolveTenantId(HttpServletRequest request) {
        // ① 优先：当前用户在 Redis 中是否有 override 租户
        Long userId = currentUserId();
        if (userId != null) {
            Long override = tenantSwitchStore.getOverride(userId);
            if (override != null) {
                log.debug("Using tenant override for user {}: tenant {}", userId, override);
                return override;
            }
        }

        // ② 其次：请求 header X-Tenant-Id（网关注入）
        String tenantIdHeader = request.getHeader(AuthHeaders.TENANT_ID);
        if (tenantIdHeader != null && !tenantIdHeader.isEmpty()) {
            try {
                return Long.parseLong(tenantIdHeader);
            } catch (NumberFormatException ignored) {
                log.warn("Invalid X-Tenant-Id header value: {}", tenantIdHeader);
            }
        }

        // ③ 兜底：JWT 中的 tenant_id claim（boot 单体无网关时）
        return jwtTenantId();
    }

    /** 从 SecurityContext 读取当前用户 ID（JWT user_id claim）。 */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return asLong(jwtAuth.getToken().getClaim("user_id"));
        }
        return null;
    }

    /** 从 SecurityContext 读取当前 JWT 的 tenant_id claim。 */
    private Long jwtTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return asLong(jwt.getClaim("tenant_id"));
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}