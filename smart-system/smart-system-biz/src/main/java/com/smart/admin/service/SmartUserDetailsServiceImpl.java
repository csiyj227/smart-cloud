package com.smart.admin.service;

import com.smart.admin.entity.SysUser;
import com.smart.admin.api.dto.UserInfo;
import com.smart.common.core.enums.StatusFlag;
import com.smart.common.core.exception.BusinessException;
import com.smart.common.security.service.SmartUser;
import com.smart.common.security.service.SmartUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implementation of SmartUserDetailsService that loads user details from the UPMS database.
 * This is the bridge between the auth server and the user management system.
 */
@Service
@RequiredArgsConstructor
public class SmartUserDetailsServiceImpl implements SmartUserDetailsService {

    private final SysUserService sysUserService;

    @Override
    public UserDetails loadUserByUsernameAndTenant(String username, Long tenantId) throws UsernameNotFoundException {
        SysUser user = sysUserService.findByUsernameAndTenant(username, tenantId);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username + " in tenant: " + tenantId);
        }

        if (StatusFlag.LOCKED.getValue().equals(user.getLockFlag())) {
            throw new BusinessException("User is locked: " + username);
        }

        Set<String> roleCodes = sysUserService.getRoleCodesByUserId(user.getUserId());
        Set<String> permissions = sysUserService.getPermissionsByUserId(user.getUserId());

        List<GrantedAuthority> authorities = new ArrayList<>();
        roleCodes.forEach(code -> authorities.add(new SimpleGrantedAuthority("ROLE_" + code)));
        permissions.forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));

        return new SmartUser(
                user.getUserId(),
                user.getUsername(),
                user.getPassword(),
                user.getDeptId(),
                user.getTenantId(),
                user.getPhone(),
                user.getAvatar(),
                user.getRealName(),
                !"1".equals(user.getStatus()),
                true,
                true,
                !StatusFlag.LOCKED.getValue().equals(user.getLockFlag()),
                authorities
        );
    }

    @Override
    public UserDetails loadUserByPhoneAndTenant(String phone, Long tenantId) throws UsernameNotFoundException {
        SysUser user = sysUserService.findByPhoneAndTenant(phone, tenantId);
        if (user == null) {
            throw new UsernameNotFoundException("User not found by phone: " + phone + " in tenant: " + tenantId);
        }

        // Reuse the same logic
        return loadUserByUsernameAndTenant(user.getUsername(), tenantId);
    }
}