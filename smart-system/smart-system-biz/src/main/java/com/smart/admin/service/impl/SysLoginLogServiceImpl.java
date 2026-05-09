package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysLoginLog;
import com.smart.admin.mapper.SysLoginLogMapper;
import com.smart.admin.service.SysLoginLogService;
import com.smart.common.core.enums.LoginType;
import com.smart.common.core.enums.StatusFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Login log service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog>
        implements SysLoginLogService {

    @Override
    public void recordLoginSuccess(Long userId, String username, Long tenantId, String ip, String userAgent) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUserId(userId);
        loginLog.setUsername(username);
        loginLog.setTenantId(tenantId);
        loginLog.setLoginType(LoginType.LOGIN.getValue());
        loginLog.setStatus(StatusFlag.SUCCESS.getValue());
        loginLog.setIp(ip);
        loginLog.setUserAgent(userAgent);
        loginLog.setMsg("Login successful");
        save(loginLog);
        log.debug("Recorded login success for user: {} in tenant: {}", username, tenantId);
    }

    @Override
    public void recordLoginFailure(String username, Long tenantId, String ip, String userAgent, String msg) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUsername(username);
        loginLog.setTenantId(tenantId);
        loginLog.setLoginType(LoginType.PASSWORD_ERROR.getValue());
        loginLog.setStatus(StatusFlag.FAIL.getValue());
        loginLog.setIp(ip);
        loginLog.setUserAgent(userAgent);
        loginLog.setMsg(msg);
        save(loginLog);
        log.debug("Recorded login failure for user: {} in tenant: {}", username, tenantId);
    }

    @Override
    public void recordLogout(Long userId, String username, Long tenantId, String ip, String userAgent) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUserId(userId);
        loginLog.setUsername(username);
        loginLog.setTenantId(tenantId);
        loginLog.setLoginType(LoginType.LOGOUT.getValue());
        loginLog.setStatus(StatusFlag.SUCCESS.getValue());
        loginLog.setIp(ip);
        loginLog.setUserAgent(userAgent);
        loginLog.setMsg("Logout successful");
        save(loginLog);
        log.debug("Recorded logout for user: {} in tenant: {}", username, tenantId);
    }

    @Override
    public void recordAccountLocked(String username, Long tenantId, String ip, String userAgent) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUsername(username);
        loginLog.setTenantId(tenantId);
        loginLog.setLoginType(LoginType.LOCKED.getValue());
        loginLog.setStatus(StatusFlag.FAIL.getValue());
        loginLog.setIp(ip);
        loginLog.setUserAgent(userAgent);
        loginLog.setMsg("Account locked due to too many failed attempts");
        save(loginLog);
        log.debug("Recorded account locked for user: {} in tenant: {}", username, tenantId);
    }

    @Override
    public int clearBefore(int days) {
        if (days <= 0) {
            return 0;
        }
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        QueryWrapper<SysLoginLog> wrapper = new QueryWrapper<>();
        wrapper.lt("create_time", threshold);
        int affected = baseMapper.delete(wrapper);
        log.info("Cleared {} sys_login_log records older than {} days", affected, days);
        return affected;
    }
}