package com.smart.admin.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.job.entity.SysJob;
import com.smart.admin.job.mapper.SysJobMapper;
import com.smart.admin.job.quartz.SmartQuartzJob;
import com.smart.admin.job.service.SysJobService;
import com.smart.common.core.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 定时任务管理 Service 实现：把 sys_job 的 CRUD 同步到 Quartz Scheduler。
 *
 * <p>JobKey/TriggerKey 命名约定：
 * <pre>
 *   JobKey     = jobGroup.jobName-jobId
 *   TriggerKey = jobGroup.jobName-jobId
 * </pre>
 * 加 jobId 后缀是为了避免不同租户/同名任务冲突。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysJobServiceImpl extends ServiceImpl<SysJobMapper, SysJob> implements SysJobService {

    private final Scheduler scheduler;

    @Override
    public IPage<SysJob> pageJob(Page<SysJob> page, String keyword, String status) {
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<SysJob>()
                .like(keyword != null && !keyword.isBlank(), SysJob::getJobName, keyword)
                .eq(status != null && !status.isBlank(), SysJob::getStatus, status)
                .orderByDesc(SysJob::getJobId);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createJob(SysJob job) {
        validateCron(job.getCronExpression());
        if (job.getStatus() == null) job.setStatus("1");
        if (job.getJobGroup() == null || job.getJobGroup().isBlank()) job.setJobGroup("DEFAULT");
        if (job.getMisfirePolicy() == null) job.setMisfirePolicy("1");
        if (job.getConcurrent() == null) job.setConcurrent("1");
        save(job);
        if ("1".equals(job.getStatus())) {
            scheduleJob(job);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateJob(SysJob job) {
        validateCron(job.getCronExpression());
        SysJob old = getById(job.getJobId());
        if (old == null) {
            throw new BusinessException("Job not found: " + job.getJobId());
        }
        updateById(job);
        // 不论旧状态如何，先反注册再按新状态注册，避免漂移
        unscheduleQuietly(old);
        if ("1".equals(job.getStatus())) {
            scheduleJob(job);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteJob(Long jobId) {
        SysJob job = getById(jobId);
        if (job == null) return;
        unscheduleQuietly(job);
        removeById(jobId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pause(Long jobId) {
        SysJob job = getById(jobId);
        if (job == null) return;
        try {
            scheduler.pauseJob(jobKey(job));
        } catch (SchedulerException e) {
            throw new BusinessException("Pause failed: " + e.getMessage());
        }
        update(new LambdaUpdateWrapper<SysJob>().eq(SysJob::getJobId, jobId).set(SysJob::getStatus, "0"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long jobId) {
        SysJob job = getById(jobId);
        if (job == null) return;
        try {
            JobKey key = jobKey(job);
            if (!scheduler.checkExists(key)) {
                scheduleJob(job);
            } else {
                scheduler.resumeJob(key);
            }
        } catch (SchedulerException e) {
            throw new BusinessException("Resume failed: " + e.getMessage());
        }
        update(new LambdaUpdateWrapper<SysJob>().eq(SysJob::getJobId, jobId).set(SysJob::getStatus, "1"));
    }

    @Override
    public void runOnce(Long jobId) {
        SysJob job = getById(jobId);
        if (job == null) {
            throw new BusinessException("Job not found: " + jobId);
        }
        try {
            JobDataMap data = buildDataMap(job, "MANUAL");
            // 一次性 trigger 命名带 nanoTime，避免持久化模式下的命名冲突
            // SimpleTrigger 不重复时 Quartz 会在执行结束后自动从 qrtz_triggers 删除（withRepeatCount(0) 默认值）
            TriggerKey tk = TriggerKey.triggerKey(
                    triggerName(job) + "-once-" + System.nanoTime(),
                    job.getJobGroup());
            JobKey jk = jobKey(job);

            if (!scheduler.checkExists(jk)) {
                // 任务未注册（暂停态），先注册一个不带 cron 触发器的 jobDetail
                JobDetail detail = JobBuilder.newJob(jobClass(job))
                        .withIdentity(jk)
                        .storeDurably(true)
                        .usingJobData(new JobDataMap()) // 空，运行时由 trigger 的 dataMap 合并
                        .build();
                scheduler.addJob(detail, true);
            }

            Trigger oneShot = TriggerBuilder.newTrigger()
                    .withIdentity(tk)
                    .forJob(jk)
                    .usingJobData(data)
                    .startAt(new Date(System.currentTimeMillis() + 100))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withRepeatCount(0)                                  // 不重复，触发一次后 Quartz 自动清理
                            .withMisfireHandlingInstructionFireNow())
                    .build();
            scheduler.scheduleJob(oneShot);
        } catch (SchedulerException e) {
            throw new BusinessException("Run once failed: " + e.getMessage());
        }
    }

    @Override
    public void reloadAll() {
        List<SysJob> all = list(new LambdaQueryWrapper<SysJob>().eq(SysJob::getStatus, "1"));
        int ok = 0, fail = 0;
        for (SysJob job : all) {
            try {
                unscheduleQuietly(job);
                scheduleJob(job);
                ok++;
            } catch (Exception e) {
                fail++;
                log.warn("Reload job failed jobId={}: {}", job.getJobId(), e.getMessage());
            }
        }
        log.info("Quartz reload finished. ok={}, fail={}, total={}", ok, fail, all.size());
    }

    @PostConstruct
    public void init() {
        // 应用启动时自动加载所有正常态任务
        try {
            reloadAll();
        } catch (Exception e) {
            log.warn("Quartz reloadAll on startup failed: {}", e.getMessage());
        }
    }

    // ─────────── Quartz 私有辅助 ───────────

    private void scheduleJob(SysJob job) {
        try {
            JobKey jk = jobKey(job);
            TriggerKey tk = triggerKey(job);

            JobDetail detail = JobBuilder.newJob(jobClass(job))
                    .withIdentity(jk)
                    .storeDurably(true)
                    .build();
            // 把任务实体放进 jobDataMap，给 SmartQuartzJob 使用
            JobDataMap data = buildDataMap(job, "CRON");

            CronScheduleBuilder cronBuilder = CronScheduleBuilder.cronSchedule(job.getCronExpression())
                    .withMisfireHandlingInstructionDoNothing();
            switch (job.getMisfirePolicy() == null ? "1" : job.getMisfirePolicy()) {
                case "1" -> cronBuilder = cronBuilder.withMisfireHandlingInstructionFireAndProceed();
                case "2" -> cronBuilder = cronBuilder.withMisfireHandlingInstructionDoNothing();
                case "3" -> cronBuilder = cronBuilder.withMisfireHandlingInstructionIgnoreMisfires();
            }
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(tk)
                    .forJob(jk)
                    .usingJobData(data)
                    .withSchedule(cronBuilder)
                    .build();

            // 已存在就替换，避免重复异常
            if (scheduler.checkExists(jk)) {
                scheduler.deleteJob(jk);
            }
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            throw new BusinessException("Schedule failed: " + e.getMessage());
        }
    }

    private void unscheduleQuietly(SysJob job) {
        try {
            scheduler.deleteJob(jobKey(job));
        } catch (SchedulerException e) {
            log.warn("Unschedule job {} failed: {}", job.getJobId(), e.getMessage());
        }
    }

    private static JobDataMap buildDataMap(SysJob job, String triggerType) {
        JobDataMap m = new JobDataMap();
        m.put(SmartQuartzJob.KEY_JOB_DATA, job);
        m.put(SmartQuartzJob.KEY_TRIGGER_TYPE, triggerType);
        return m;
    }

    private static JobKey jobKey(SysJob job) {
        return JobKey.jobKey(jobName(job), job.getJobGroup());
    }

    private static TriggerKey triggerKey(SysJob job) {
        return TriggerKey.triggerKey(triggerName(job), job.getJobGroup());
    }

    private static String jobName(SysJob job) {
        return "JOB-" + job.getJobId();
    }

    private static String triggerName(SysJob job) {
        return "TRG-" + job.getJobId();
    }

    private static Class<? extends org.quartz.Job> jobClass(SysJob job) {
        // 显式向上转型为 Class<? extends Job>，避免 javac 在三元表达式上推断为 Class<? extends SmartQuartzJob>
        @SuppressWarnings("unchecked")
        Class<? extends org.quartz.Job> klass = "1".equals(job.getConcurrent())
                ? (Class<? extends org.quartz.Job>) SmartQuartzJob.DisallowConcurrentSmartQuartzJob.class
                : (Class<? extends org.quartz.Job>) SmartQuartzJob.class;
        return klass;
    }

    private static void validateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new BusinessException("Cron expression is required");
        }
        try {
            CronScheduleBuilder.cronSchedule(cron);
        } catch (Exception e) {
            throw new BusinessException("Invalid cron expression: " + cron);
        }
    }
}
