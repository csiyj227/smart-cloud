package com.smart.admin.job.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.job.entity.SysJobLog;

public interface SysJobLogService extends IService<SysJobLog> {

    IPage<SysJobLog> pageLog(Page<SysJobLog> page, Long jobId, String status);

    /** 清理超过 days 天的日志（被定时任务调用） */
    int cleanExpired(int days);
}
