package com.smart.common.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Interface for loading user details during OAuth2 authentication.
 * Implementations in the SYSTEM module fetch user data from the database.
 *
 * OAuth2 认证过程中加载用户详情的接口。
 * 实现类在 SYSTEM 模块中从数据库获取用户数据。
 */
public interface SmartUserDetailsService {

    /**
     * Load user by username within a specific tenant.
     */
    UserDetails loadUserByUsernameAndTenant(String username, Long tenantId) throws UsernameNotFoundException;

    /**
     * Load user by phone number for SMS login.
     */
    UserDetails loadUserByPhoneAndTenant(String phone, Long tenantId) throws UsernameNotFoundException;
}