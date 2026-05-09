package com.smart.common.security.service;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Extended UserDetails that carries rich user context for microservice authorization.
 * Implements OAuth2AuthenticatedPrincipal so it can serve as the token introspection result.
 *
 * 扩展的 UserDetails，携带丰富的用户上下文信息用于微服务授权。
 * 实现 OAuth2AuthenticatedPrincipal 接口，因此可以作为令牌内省的结果使用。
 */
@Getter
public class SmartUser extends User implements OAuth2AuthenticatedPrincipal {

    private final Long userId;
    private final Long deptId;
    private final Long tenantId;
    private final String phone;
    private final String avatar;
    private final String realName;
    private final Map<String, Object> attributes;

    public SmartUser(Long userId, String username, String password, Long deptId, Long tenantId,
                     String phone, String avatar, String realName,
                     boolean enabled, boolean accountNonExpired,
                     boolean credentialsNonExpired, boolean accountNonLocked,
                     Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);
        this.userId = userId;
        this.deptId = deptId;
        this.tenantId = tenantId;
        this.phone = phone;
        this.avatar = avatar;
        this.realName = realName;
        this.attributes = new HashMap<>();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return getUsername();
    }

    public SmartUser setAttribute(String key, Object value) {
        this.attributes.put(key, value);
        return this;
    }
}