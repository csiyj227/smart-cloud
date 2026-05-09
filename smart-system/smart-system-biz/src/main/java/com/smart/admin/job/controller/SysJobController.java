package com.smart.admin.job.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.job.entity.SysJob;
import com.smart.admin.job.service.SysJobDepService;
import com.smart.admin.job.service.SysJobService;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/job")
@RequiredArgsConstructor
public class SysJobController {

    private final SysJobService jobService;
    private final SysJobDepService depService;

    @GetMapping("/page")
    public ApiResult<IPage<SysJob>> page(@RequestParam(defaultValue = "1") long current,
                                 @RequestParam(defaultValue = "10") long size,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String status) {
        return ApiResult.success(jobService.pageJob(new Page<>(current, size), keyword, status));
    }

    @GetMapping("/{jobId}")
    public ApiResult<SysJob> detail(@PathVariable Long jobId) {
        return ApiResult.success(jobService.getById(jobId));
    }

    @PostMapping
    public ApiResult<Void> create(@RequestBody SysJob job) {
        jobService.createJob(job);
        return ApiResult.success();
    }

    @PutMapping
    public ApiResult<Void> update(@RequestBody SysJob job) {
        jobService.updateJob(job);
        return ApiResult.success();
    }

    @DeleteMapping("/{jobId}")
    public ApiResult<Void> delete(@PathVariable Long jobId) {
        jobService.deleteJob(jobId);
        return ApiResult.success();
    }

    @PutMapping("/{jobId}/pause")
    public ApiResult<Void> pause(@PathVariable Long jobId) {
        jobService.pause(jobId);
        return ApiResult.success();
    }

    @PutMapping("/{jobId}/resume")
    public ApiResult<Void> resume(@PathVariable Long jobId) {
        jobService.resume(jobId);
        return ApiResult.success();
    }

    @PostMapping("/{jobId}/run")
    public ApiResult<Void> runOnce(@PathVariable Long jobId) {
        jobService.runOnce(jobId);
        return ApiResult.success();
    }

    // ─────────── 任务依赖 ───────────
    @GetMapping("/{jobId}/upstreams")
    public ApiResult<List<Long>> upstreams(@PathVariable Long jobId) {
        return ApiResult.success(depService.listUpstreams(jobId));
    }

    @GetMapping("/{jobId}/downstreams")
    public ApiResult<List<Long>> downstreams(@PathVariable Long jobId) {
        return ApiResult.success(depService.listDownstreams(jobId));
    }

    @PutMapping("/{jobId}/upstreams")
    public ApiResult<Void> resetUpstreams(@PathVariable Long jobId, @RequestBody List<Long> upstreamIds) {
        depService.resetUpstreams(jobId, upstreamIds);
        return ApiResult.success();
    }
}
