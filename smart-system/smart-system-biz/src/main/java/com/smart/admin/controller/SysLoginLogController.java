package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.entity.SysLoginLog;
import com.smart.admin.service.SysLoginLogService;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Login log controller.
 *
 * 登录日志控制器。
 */
@Slf4j
@RestController
@RequestMapping("/system/login-log")
@RequiredArgsConstructor
public class SysLoginLogController {

    private final SysLoginLogService sysLoginLogService;

    /**
     * Query login logs with pagination.
     */
    @PreAuthorize("@authz.hasPermission('sys_login_log_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysLoginLog>> page(Page<SysLoginLog> page, SysLoginLog query) {
        return ApiResult.success(sysLoginLogService.page(page, Wrappers.<SysLoginLog>lambdaQuery()
                .like(query.getUsername() != null && !query.getUsername().isEmpty(), SysLoginLog::getUsername, query.getUsername())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), SysLoginLog::getStatus, query.getStatus())
                .eq(query.getLoginType() != null && !query.getLoginType().isEmpty(), SysLoginLog::getLoginType, query.getLoginType())
                .orderByDesc(SysLoginLog::getCreateTime)));
    }

    /**
     * Get login log by ID.
     */
    @PreAuthorize("@authz.hasPermission('sys_login_log_view')")
    @GetMapping("/{id}")
    public ApiResult<SysLoginLog> getById(@PathVariable Long id) {
        return ApiResult.success(sysLoginLogService.getById(id));
    }

    /**
     * Clear login logs older than the given days.
     */
    @PreAuthorize("@authz.hasPermission('sys_login_log_del')")
    @DeleteMapping("/clear")
    public ApiResult<Integer> clear(@RequestParam(value = "days", defaultValue = "30") int days) {
        return ApiResult.success(sysLoginLogService.clearBefore(days));
    }
}