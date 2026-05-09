package com.smart.common.security.component;

import com.smart.common.security.service.SmartUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permission evaluation service registered as Spring bean "authz".
 * Used in @PreAuthorize expressions: @PreAuthorize("@authz.hasPermission('sys_user_add')")
 *
 * 权限评估服务，注册为 Spring Bean "authz"。
 * 在 @PreAuthorize 表达式中使用，例如：@PreAuthorize("@authz.hasPermission('sys_user_add')")
 */
@Slf4j
@Component("authz")
public class PermissionEvaluator {

    /**
     * Check if the current user has the specified permission.
     * Super-admin (ROLE_ADMIN) always returns true.
     */
    public boolean hasPermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // Super admin has all permissions
        if (authorities.contains("ROLE_ADMIN")) {
            return true;
        }

        // Check wildcard permission
        if (authorities.contains("*:*:*")) {
            return true;
        }

        return authorities.contains(permission);
    }

    /**
     * Check if the current user has any of the specified permissions.
     */
    public boolean hasAnyPermission(String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the current user has the specified role.
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_" + role));
    }

    /**
     * Get the current user's tenant ID, or null if not authenticated.
     */
    public Long getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SmartUser smartUser) {
            return smartUser.getTenantId();
        }
        if (principal instanceof Jwt jwt) {
            return toLong(firstNonNull(jwt.getClaim("tenant_id"), jwt.getClaim("tenantId")));
        }
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth2Principal) {
            return toLong(oauth2Principal.getAttribute("tenant_id"));
        }
        return null;
    }

    /**
     * Get the current user's display name (realName), or null if not authenticated.
     */
    public String getCurrentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SmartUser smartUser) {
            return smartUser.getRealName();
        }
        if (principal instanceof Jwt jwt) {
            Object name = firstNonNull(jwt.getClaim("real_name"), jwt.getClaim("realName"), jwt.getClaim("name"));
            return name != null ? name.toString() : null;
        }
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth2Principal) {
            Object name = oauth2Principal.getAttribute("real_name");
            return name != null ? name.toString() : null;
        }
        return null;
    }

    /**
     * Get the current user's user ID, or null if not authenticated.
     */
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SmartUser smartUser) {
            return smartUser.getUserId();
        }
        if (principal instanceof Jwt jwt) {
            return toLong(firstNonNull(
                    jwt.getClaim("user_id"), jwt.getClaim("userId"),
                    jwt.getClaim("uid"), jwt.getClaim("sub")));
        }
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth2Principal) {
            return toLong(oauth2Principal.getAttribute("user_id"));
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Long longVal) {
            return longVal;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isEmpty()) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return null;
    }
}