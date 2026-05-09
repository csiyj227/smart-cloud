package com.smart.admin.job.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.job.entity.SysJob;

/**
 * 定时任务管理 Service。
 * 把 sys_job 的变更同步到 Quartz Scheduler 是本接口的核心职责。
 */
public interface SysJobService extends IService<SysJob> {

    IPage<SysJob> pageJob(Page<SysJob> page, String keyword, String status);

    /** 新增任务（save + scheduler 注册） */
    void createJob(SysJob job);

    /** 更新任务（update + scheduler 重建 trigger） */
    void updateJob(SysJob job);

    /** 删除任务（remove + scheduler 反注册） */
    void deleteJob(Long jobId);

    /** 暂停 */
    void pause(Long jobId);

    /** 恢复 */
    void resume(Long jobId);

    /** 立即执行一次（不影响 cron 后续节奏） */
    void runOnce(Long jobId);

    /** 启动时把所有数据库里 status=1 的任务重新装载到 Scheduler */
    void reloadAll();
}
