package com.smart.admin.job.quartz;

import com.smart.admin.job.entity.SysJob;
import com.smart.admin.job.entity.SysJobLog;
import com.smart.admin.job.service.SysJobAlertService;
import com.smart.admin.job.service.SysJobDepService;
import com.smart.admin.job.service.SysJobLogService;
import com.smart.common.core.tenant.TenantContext;
import com.smart.common.core.spring.ApplicationContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 真正被 Quartz 触发执行的 Job 类。
 *
 * <p>所有 sys_job 都映射到本类（或子类 {@link DisallowConcurrentSmartQuartzJob}），
 * 通过 JobDataMap 拿到 {@link SysJob} 配置，转交 {@link JobInvoker} 反射调用业务 Bean。
 *
 * <p>同时负责：
 * <ul>
 *   <li>写 sys_job_log（成功/失败 + 耗时 + 异常栈）</li>
 *   <li>失败时调 SysJobAlertService 发通知</li>
 *   <li>成功时通过 SysJobDepService 触发下游依赖任务</li>
 * </ul>
 */
@Slf4j
public class SmartQuartzJob implements Job {

    /** JobDataMap 的 key（写入完整 SysJob 实体） */
    public static final String KEY_JOB_DATA = "smartJob";
    /** 触发类型：CRON / MANUAL / DEPENDENCY */
    public static final String KEY_TRIGGER_TYPE = "triggerType";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getMergedJobDataMap();
        SysJob job = (SysJob) data.get(KEY_JOB_DATA);
        String triggerType = (String) data.getOrDefault(KEY_TRIGGER_TYPE, "CRON");
        if (job == null) {
            log.warn("SmartQuartzJob fired without payload, skipping");
            return;
        }

        SysJobLog logEntity = newLog(job, triggerType);
        long start = System.currentTimeMillis();
        Date fireTime = context.getFireTime();
        if (fireTime != null) {
            logEntity.setStartTime(LocalDateTime.ofInstant(fireTime.toInstant(), ZoneId.systemDefault()));
        }

        // 关键：把 job 的 tenantId 注入当前 Quartz worker 线程的 TenantContext，
        // 这样任务体里 SysFileService / SysJobLogService / SysNoticeService 等所有
        // @TenantEntity 表的查询/写入都能正确按租户隔离，不再串户。
        // finally 里必须 clear，避免 Quartz worker 线程被复用时残留上一个任务的 tenant。
        Long previousTenant = TenantContext.get().orElse(null);
        if (job.getTenantId() != null) {
            TenantContext.set(job.getTenantId());
        }

        try {
            String result = JobInvoker.invoke(job.getInvokeTarget(), job.getJobParam());
            logEntity.setStatus("0");
            logEntity.setResult(result == null ? "OK" : truncate(result, 4000));
            // 触发下游依赖
            depServiceLazy().triggerDependents(job.getJobId());
        } catch (Throwable e) {
            logEntity.setStatus("1");
            logEntity.setExceptionInfo(truncate(stackTraceToString(e), 8000));
            // 失败报警
            if (Boolean.TRUE.equals(job.getAlertOnFailure())) {
                try {
                    alertServiceLazy().sendFailureAlert(job, e);
                } catch (Exception alertEx) {
                    log.warn("Failed to send job alert: {}", alertEx.getMessage());
                }
            }
        } finally {
            long cost = System.currentTimeMillis() - start;
            logEntity.setEndTime(LocalDateTime.now());
            logEntity.setDurationMs(cost);
            try {
                logServiceLazy().save(logEntity);
            } catch (Exception logEx) {
                log.warn("Failed to write job log: {}", logEx.getMessage());
            }
            // 还原 worker 线程的租户上下文
            if (previousTenant != null) {
                TenantContext.set(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private static SysJobLog newLog(SysJob job, String triggerType) {
        SysJobLog l = new SysJobLog();
        l.setJobId(job.getJobId());
        l.setJobName(job.getJobName());
        l.setJobGroup(job.getJobGroup());
        l.setInvokeTarget(job.getInvokeTarget());
        l.setJobParam(job.getJobParam());
        l.setTriggerType(triggerType);
        l.setTenantId(job.getTenantId());
        return l;
    }

    private static String stackTraceToString(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    // 通过 ApplicationContextProvider 懒查 Service，避免 Job 类被 newInstance 时 NPE
    private static SysJobLogService logServiceLazy() {
        return ApplicationContextProvider.getBean(SysJobLogService.class);
    }

    private static SysJobDepService depServiceLazy() {
        return ApplicationContextProvider.getBean(SysJobDepService.class);
    }

    private static SysJobAlertService alertServiceLazy() {
        return ApplicationContextProvider.getBean(SysJobAlertService.class);
    }

    /** 标记禁止并发的子类（concurrent='1' 走这个） */
    @DisallowConcurrentExecution
    public static class DisallowConcurrentSmartQuartzJob extends SmartQuartzJob {}
}
