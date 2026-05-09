package com.smart.admin.job.task;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.smart.admin.file.service.SysFileService;
import com.smart.admin.job.service.SysJobLogService;
import com.smart.admin.service.SysLoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 内置示例任务集合。
 *
 * <p>所有方法都会被 {@link com.smart.admin.job.quartz.JobInvoker} 通过 bean 名 + 方法名反射调用。
 * 由 sys_job 表里的 invoke_target 直接指定，例如：
 * <pre>
 *   invoke_target = "sysJobInternalTask.cleanLoginLog"
 *   job_param     = {"days":30}
 * </pre>
 *
 * <p>方法签名约定：单 String 参数（jobParam JSON）或无参，返回 String 写入执行日志的 result。
 */
@Slf4j
@Component("sysJobInternalTask")
@RequiredArgsConstructor
public class SysJobInternalTask {

    private final SysFileService sysFileService;
    private final SysJobLogService sysJobLogService;
    private final SysLoginLogService sysLoginLogService;

    /** 清理过期登录日志：直接调用 SysLoginLogService#clearBefore */
    public String cleanLoginLog(String jobParam) {
        int days = parseDays(jobParam, 30);
        int affected = sysLoginLogService.clearBefore(days);
        return "cleanLoginLog days=" + days + ", deleted=" + affected;
    }

    /** 清理回收站超过 N 天的文件（真正调 SysFileService） */
    public String cleanRecycleFile(String jobParam) {
        int days = parseDays(jobParam, 30);
        int count = sysFileService.cleanRecycleExpired(days);
        return "cleanRecycleFile days=" + days + ", purged=" + count;
    }

    /** 清理超过 N 天的任务执行日志 */
    public String cleanJobLog(String jobParam) {
        int days = parseDays(jobParam, 30);
        int count = sysJobLogService.cleanExpired(days);
        return "cleanJobLog days=" + days + ", deleted=" + count;
    }

    /** 心跳测试任务（用于验证 cron + 反射 + 日志全链路） */
    public String heartbeat() {
        log.info("[InternalTask] heartbeat at {}", System.currentTimeMillis());
        return "OK";
    }

    private static int parseDays(String jobParam, int defaultDays) {
        if (jobParam == null || jobParam.isBlank()) return defaultDays;
        try {
            JSONObject obj = JSONUtil.parseObj(jobParam);
            Object days = obj.get("days");
            if (days != null) {
                return Integer.parseInt(String.valueOf(days));
            }
        } catch (Exception ignored) {}
        return defaultDays;
    }
}
