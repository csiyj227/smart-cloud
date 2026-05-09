package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.api.dto.SysLogDTO;
import com.smart.admin.entity.SysLog;
import com.smart.admin.service.SysLogService;
import com.smart.common.core.web.ApiResult;
import com.smart.common.log.annotation.AuditTrace;
import com.smart.common.security.annotation.ServiceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Log management REST API.
 *
 * 日志管理 REST 接口。
 */
@RestController
@RequestMapping("/system/log")
@RequiredArgsConstructor
public class SysLogController {

    private final SysLogService sysLogService;

    @ServiceApi
    @PostMapping
    public ApiResult<Void> saveLog(@RequestBody SysLogDTO logDTO) {
        sysLogService.saveLog(logDTO);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_log_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysLog>> page(Page<SysLog> page, SysLog query) {
        return ApiResult.success(sysLogService.page(page, Wrappers.<SysLog>lambdaQuery()
                .eq(query.getLogType() != null && !query.getLogType().isEmpty(), SysLog::getLogType, query.getLogType())
                .like(query.getTitle() != null && !query.getTitle().isEmpty(), SysLog::getTitle, query.getTitle())
                .like(query.getCreateBy() != null && !query.getCreateBy().isEmpty(), SysLog::getCreateBy, query.getCreateBy())
                .orderByDesc(SysLog::getCreateTime)));
    }

    @PreAuthorize("@authz.hasPermission('sys_log_view')")
    @GetMapping("/{id}")
    public ApiResult<SysLog> getById(@PathVariable Long id) {
        return ApiResult.success(sysLogService.getById(id));
    }

    /**
     * 清理 N 天前的操作日志（默认 30 天）。
     */
    @PreAuthorize("@authz.hasPermission('sys_log_del')")
    @AuditTrace("清理操作日志")
    @DeleteMapping("/clear")
    public ApiResult<Integer> clear(@RequestParam(value = "days", defaultValue = "30") int days) {
        return ApiResult.success(sysLogService.clearBefore(days));
    }
}