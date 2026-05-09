package com.smart.admin.job.service;

import com.smart.admin.job.entity.SysJob;

/**
 * 任务失败报警。
 * 默认实现写到 sys_notice，让用户在 通知中心 看到失败提醒。
 */
public interface SysJobAlertService {

    void sendFailureAlert(SysJob job, Throwable cause);
}
