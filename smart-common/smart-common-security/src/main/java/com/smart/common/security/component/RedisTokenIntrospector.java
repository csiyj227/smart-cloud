package com.smart.common.security.component;

import com.smart.common.core.tenant.TenantContext;
import com.smart.common.security.service.SmartUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis-backed opaque token introspector for resource servers.
 * Looks up the full OAuth2Authorization from Redis (stored by the auth server),
 * then rebuilds a SmartUser principal with tenant and permission information.
 * Also populates TenantContext so the MyBatis-Plus tenant interceptor
 * can filter queries by the authenticated user's tenant.
 *
 * 基于 Redis 的不透明令牌内省器，用于资源服务器。
 * 从 Redis 中查找完整的 OAuth2Authorization（由认证服务器存储），
 * 然后重建带有租户和权限信息的 SmartUser 主体。
 * 同时填充 TenantContextHolder，以便 MyBatis-Plus 租户拦截器
 * 可以按已认证用户的租户过滤查询。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisTokenIntrospector implements OpaqueTokenIntrospector {

    private final OAuth2AuthorizationService authorizationService;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2Authorization authorization = authorizationService.findByToken(token, null);
        if (authorization == null) {
            log.debug("Token not found in authorization store");
            return null;
        }

        // If token is invalidated, reject
        if (authorization.getToken(token) != null
                && authorization.getToken(token).isInvalidated()) {
            log.debug("Token is invalidated");
            return null;
        }

        // Extract the principal from the authorization
        Object principal = authorization.getAttribute("java.security.Principal");
        if (principal instanceof SmartUser smartUser) {
            return buildFromSmartUser(authorization, smartUser);
        }
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth2Principal) {
            return buildFromOAuth2Principal(authorization, oauth2Principal);
        }
        if (principal instanceof Map<?, ?> principalMap) {
            return buildFromMapPrincipal(authorization, principalMap);
        }

        OAuth2AuthenticatedPrincipal principalFromAttributes = buildFromAuthorizationAttributes(authorization);
        if (principalFromAttributes != null) {
            return principalFromAttributes;
        }

        // Fallback: build from client attributes only (client_credentials etc.)
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("client_id", authorization.getRegisteredClientId());
        return new SimpleOAuth2Principal("system", attributes);
    }


    private OAuth2AuthenticatedPrincipal buildFromSmartUser(OAuth2Authorization authorization, SmartUser smartUser) {
        if (smartUser.getTenantId() != null && TenantContext.get() == null) {
            TenantContext.set(smartUser.getTenantId());
        }

        Map<String, Object> attributes = new HashMap<>(smartUser.getAttributes());
        attributes.put("client_id", authorization.getRegisteredClientId());
        attributes.put("tenant_id", smartUser.getTenantId());
        attributes.put("user_id", smartUser.getUserId());
        attributes.put("dept_id", smartUser.getDeptId());
        attributes.put("real_name", smartUser.getRealName());
        attributes.put("phone", smartUser.getPhone());
        attributes.put("avatar", smartUser.getAvatar());
        attributes.putIfAbsent("username", smartUser.getUsername());
        return new SmartUserPrincipal(smartUser, attributes);
    }

    private OAuth2AuthenticatedPrincipal buildFromOAuth2Principal(OAuth2Authorization authorization,
                                                                  OAuth2AuthenticatedPrincipal oauth2Principal) {
        Map<String, Object> attributes = new HashMap<>(oauth2Principal.getAttributes());
        attributes.put("client_id", authorization.getRegisteredClientId());
        applyTenantContext(attributes);

        Long userId = asLong(firstNonNull(
                attributes.get("user_id"),
                authorization.getAttribute("user_id")
        ));
        if (userId != null) {
            return new SimpleOAuth2Principal(resolvePrincipalName(oauth2Principal.getName(), attributes),
                    mergeAuthorizationAttributes(authorization, attributes),
                    oauth2Principal.getAuthorities());
        }
        return new SimpleOAuth2Principal(oauth2Principal.getName(), attributes, oauth2Principal.getAuthorities());
    }

    private OAuth2AuthenticatedPrincipal buildFromMapPrincipal(OAuth2Authorization authorization,
                                                               Map<?, ?> principalMap) {
        Map<String, Object> attributes = mapToStringObject(principalMap);
        attributes.put("client_id", authorization.getRegisteredClientId());
        Map<String, Object> merged = mergeAuthorizationAttributes(authorization, attributes);
        applyTenantContext(merged);

        String principalName = resolvePrincipalName(authorization.getPrincipalName(), merged);
        Collection<? extends GrantedAuthority> authorities = extractAuthorities(merged.get("authorities"));
        Long userId = asLong(merged.get("user_id"));
        if (userId != null) {
            return new SimpleOAuth2Principal(principalName, merged, authorities);
        }
        return new SimpleOAuth2Principal(principalName != null ? principalName : "system", merged, authorities);
    }

    private OAuth2AuthenticatedPrincipal buildFromAuthorizationAttributes(OAuth2Authorization authorization) {
        Long userId = asLong(authorization.getAttribute("user_id"));
        if (userId == null) {
            return null;
        }
        Map<String, Object> attributes = mergeAuthorizationAttributes(authorization, new HashMap<>());
        attributes.put("client_id", authorization.getRegisteredClientId());
        applyTenantContext(attributes);
        return new SimpleOAuth2Principal(resolvePrincipalName(authorization.getPrincipalName(), attributes),
                attributes,
                extractAuthorities(attributes.get("authorities")));
    }

    private Map<String, Object> mergeAuthorizationAttributes(OAuth2Authorization authorization, Map<String, Object> base) {
        Map<String, Object> merged = new HashMap<>(base);
        copyIfPresent(merged, "user_id", authorization.getAttribute("user_id"));
        copyIfPresent(merged, "tenant_id", authorization.getAttribute("tenant_id"));
        copyIfPresent(merged, "dept_id", authorization.getAttribute("dept_id"));
        copyIfPresent(merged, "username", authorization.getAttribute("username"));
        copyIfPresent(merged, "real_name", authorization.getAttribute("real_name"));
        copyIfPresent(merged, "phone", authorization.getAttribute("phone"));
        copyIfPresent(merged, "avatar", authorization.getAttribute("avatar"));
        copyIfPresent(merged, "authorities", authorization.getAttribute("authorities"));
        merged.put("client_id", authorization.getRegisteredClientId());
        return merged;
    }

    private void copyIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !target.containsKey(key)) {
            target.put(key, value);
        }
    }

    private void applyTenantContext(Map<String, Object> attributes) {
        Long tenantId = asLong(attributes.get("tenant_id"));
        if (tenantId != null && TenantContext.get() == null) {
            TenantContext.set(tenantId);
        }
    }

    private Map<String, Object> mapToStringObject(Map<?, ?> source) {
        Map<String, Object> result = new HashMap<>();
        source.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
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

    private String resolvePrincipalName(String defaultName, Map<String, Object> attributes) {
        Object username = firstNonNull(
                attributes.get("username"),
                attributes.get("user_name"),
                attributes.get("name"),
                attributes.get("sub")
        );
        return username != null ? String.valueOf(username) : (defaultName != null ? defaultName : "system");
    }

    private Collection<? extends GrantedAuthority> extractAuthorities(Object rawAuthorities) {
        if (rawAuthorities instanceof Collection<?> collection) {
            return collection.stream()
                    .map(Object::toString)
                    .filter(value -> !value.isBlank())
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        }
        return java.util.List.of();
    }

    /**
     * SmartUser-based OAuth2 principal with full user context.
     */
    static class SmartUserPrincipal implements OAuth2AuthenticatedPrincipal {
        private final SmartUser smartUser;
        private final Map<String, Object> attributes;

        SmartUserPrincipal(SmartUser smartUser, Map<String, Object> attributes) {
            this.smartUser = smartUser;
            this.attributes = attributes;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return smartUser.getAuthorities();
        }

        @Override
        public String getName() {
            return smartUser.getUsername();
        }

        public Long getTenantId() {
            return smartUser.getTenantId();
        }

        public Long getUserId() {
            return smartUser.getUserId();
        }

        public Long getDeptId() {
            return smartUser.getDeptId();
        }
    }

    /**
     * Simple principal for client_credentials tokens (no user).
     */
    static class SimpleOAuth2Principal implements OAuth2AuthenticatedPrincipal {
        private final String name;
        private final Map<String, Object> attributes;
        private final Collection<? extends GrantedAuthority> authorities;

        SimpleOAuth2Principal(String name, Map<String, Object> attributes) {
            this(name, attributes, java.util.List.of());
        }

        SimpleOAuth2Principal(String name, Map<String, Object> attributes,
                              Collection<? extends GrantedAuthority> authorities) {
            this.name = name;
            this.attributes = attributes;
            this.authorities = authorities;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}