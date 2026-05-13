package com.smart.auth.service;

import com.smart.admin.api.dto.UserInfo;
import com.smart.admin.api.feign.RemoteUserService;
import com.smart.common.core.auth.AuthHeaders;
import com.smart.common.core.enums.StatusFlag;
import com.smart.common.core.web.ApiResult;
import com.smart.common.security.service.SmartUser;
import com.smart.common.security.service.SmartUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Feign-based implementation of SmartUserDetailsService for the Auth server.
 * Loads user details via remote call to the SYSTEM service instead of direct DB access.
 *
 * <p>Activated only when {@code smart.auth.feign-user-details=true} (set in smart-auth's application.yml).
 * In monolith mode (SmartBootApplication), this property is absent, so
 * the SmartUserDetailsServiceImpl from SYSTEM-biz is used instead (direct DB access).
 *
 * Feign 实现的 SmartUserDetailsService，用于认证服务器。
 * 通过远程调用 SYSTEM 服务加载用户详情，而非直接访问数据库。
 *
 * <p>仅在 {@code smart.auth.feign-user-details=true} 时激活（在 smart-auth 的 application.yml 中设置）。
 * 在单体模式（SmartBootApplication）下，此属性不存在，因此使用 SYSTEM-biz 中的 SmartUserDetailsServiceImpl（直接数据库访问）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "smart.auth.feign-user-details", havingValue = "true")
@RequiredArgsConstructor
public class FeignBasedUserDetailsService implements SmartUserDetailsService {

    private final RemoteUserService remoteUserService;

    @Override
    public UserDetails loadUserByUsernameAndTenant(String username, Long tenantId) throws UsernameNotFoundException {
        ApiResult<UserInfo> result = remoteUserService.info(username, tenantId, AuthHeaders.SERVICE_CALL_PRESENT);
        return convertToUserDetails(result, "username", username, tenantId);
    }

    @Override
    public UserDetails loadUserByPhoneAndTenant(String phone, Long tenantId) throws UsernameNotFoundException {
        ApiResult<UserInfo> result = remoteUserService.infoByPhone(phone, tenantId, AuthHeaders.SERVICE_CALL_PRESENT);
        return convertToUserDetails(result, "phone", phone, tenantId);
    }

    private UserDetails convertToUserDetails(ApiResult<UserInfo> result, String identifierType,
                                             String identifierValue, Long tenantId) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.warn("User not found by {}: {} in tenant: {}", identifierType, identifierValue, tenantId);
            throw new UsernameNotFoundException(
                    "User not found by " + identifierType + ": " + identifierValue + " in tenant: " + tenantId);
        }

        UserInfo userInfo = result.getData();

        if (StatusFlag.LOCKED.getValue().equals(userInfo.getLockFlag())) {
            log.warn("User is locked: {} in tenant: {}", userInfo.getUsername(), tenantId);
            throw new UsernameNotFoundException("User is locked: " + userInfo.getUsername());
        }

        List<GrantedAuthority> authorities = buildAuthorities(userInfo.getRoles(), userInfo.getPermissions());

        return new SmartUser(
                userInfo.getUserId(),
                userInfo.getUsername(),
                userInfo.getPassword(),
                userInfo.getDeptId(),
                userInfo.getTenantId(),
                userInfo.getPhone(),
                userInfo.getAvatar(),
                userInfo.getRealName(),
                true,
                true,
                true,
                !StatusFlag.LOCKED.getValue().equals(userInfo.getLockFlag()),
                authorities
        );
    }

    private List<GrantedAuthority> buildAuthorities(Set<String> roles, Set<String> permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        if (permissions != null) {
            permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        return authorities;
    }
}
