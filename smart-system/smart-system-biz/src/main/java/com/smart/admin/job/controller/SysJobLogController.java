package com.smart.admin.job.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.job.entity.SysJobLog;
import com.smart.admin.job.service.SysJobLogService;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/job/log")
@RequiredArgsConstructor
public class SysJobLogController {

    private final SysJobLogService jobLogService;

    @GetMapping("/page")
    public ApiResult<IPage<SysJobLog>> page(@RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size,
                                    @RequestParam(required = false) Long jobId,
                                    @RequestParam(required = false) String status) {
        return ApiResult.success(jobLogService.pageLog(new Page<>(current, size), jobId, status));
    }

    @GetMapping("/{logId}")
    public ApiResult<SysJobLog> detail(@PathVariable Long logId) {
        return ApiResult.success(jobLogService.getById(logId));
    }

    @DeleteMapping("/clean")
    public ApiResult<Integer> clean(@RequestParam(defaultValue = "30") int days) {
        return ApiResult.success(jobLogService.cleanExpired(days));
    }
}
