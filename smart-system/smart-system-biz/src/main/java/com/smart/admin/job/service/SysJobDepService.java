package com.smart.admin.job.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.job.entity.SysJobDep;

import java.util.List;

/**
 * 任务依赖管理。
 * <p>{@link #triggerDependents(Long)} 在 Job 成功执行后调用，
 * 找出依赖此任务的下游任务并立即触发执行。
 */
public interface SysJobDepService extends IService<SysJobDep> {

    /** 查询某任务的所有上游（它依赖的任务） */
    List<Long> listUpstreams(Long jobId);

    /** 查询某任务的所有下游（依赖它的任务） */
    List<Long> listDownstreams(Long jobId);

    /** 重置某任务的上游依赖（先清后插） */
    void resetUpstreams(Long jobId, List<Long> upstreamIds);

    /**
     * 当某任务成功后，触发所有依赖它的下游任务"立即执行一次"。
     * 触发类型为 DEPENDENCY，记录到 sys_job_log。
     */
    void triggerDependents(Long sourceJobId);
}
