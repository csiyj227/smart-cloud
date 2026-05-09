package com.smart.admin.job.service.impl;

import com.smart.admin.entity.SysNotice;
import com.smart.admin.job.entity.SysJob;
import com.smart.admin.job.service.SysJobAlertService;
import com.smart.admin.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

/**
 * 任务失败报警实现：把失败信息写到 sys_notice 通知中心，前端公告页可见。
 *
 * <p>{@link SysJob#getAlertUserIds()} 字段当前用作"知会名单"，仅记录在公告内容里
 * 供值班/运维查看；公告本身是租户内全员可见。后续如需"按用户精确推送"，
 * 可在 sys_notice_read 表中预先插入对应 userId 的未读记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysJobAlertServiceImpl implements SysJobAlertService {

    private final SysNoticeService sysNoticeService;

    @Override
    public void sendFailureAlert(SysJob job, Throwable cause) {
        String title = String.format("[定时任务失败] %s (id=%d)", job.getJobName(), job.getJobId());
        String content = buildContent(job, cause);
        log.warn(title);

        try {
            SysNotice notice = new SysNotice();
            notice.setNoticeTitle(title);
            notice.setNoticeType("1"); // 1=notice
            notice.setNoticeContent(content);
            notice.setPublisher("system");
            notice.setPriority("3"); // 3=high
            notice.setStatus("1"); // 1=published
            notice.setPublishTime(LocalDateTime.now());
            // 注：tenantId 由 BaseEntity + @TenantEntity + MyBatis-Plus TenantLineInnerInterceptor 自动填充。
            // Quartz 异步线程没有 TenantContext，所以 publish 落库时 tenantId 可能为默认值（0）。
            // 如果未来需要按 job.tenantId 报警，需要在 SmartQuartzJob 触发前先 TenantContext.setTenantId(job.getTenantId())
            sysNoticeService.publish(notice);
        } catch (Exception e) {
            // 报警失败不能影响主流程，仅打 warn 日志
            log.warn("Failed to publish job-failure notice for job {}: {}", job.getJobId(), e.getMessage());
        }
    }

    private static String buildContent(SysJob job, Throwable cause) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务名称: ").append(job.getJobName()).append('\n');
        sb.append("任务分组: ").append(job.getJobGroup()).append('\n');
        sb.append("调用目标: ").append(job.getInvokeTarget()).append('\n');
        sb.append("失败时间: ").append(LocalDateTime.now()).append('\n');
        sb.append("失败原因: ").append(cause.getMessage() == null
                ? cause.getClass().getSimpleName() : cause.getMessage()).append('\n');
        if (job.getAlertUserIds() != null && !job.getAlertUserIds().isBlank()) {
            sb.append("通知对象: userIds=").append(job.getAlertUserIds()).append('\n');
        }
        sb.append("\n--- 堆栈 ---\n").append(stackTrace(cause));
        return sb.toString();
    }

    private static String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 4000 ? s.substring(0, 4000) + "...(truncated)" : s;
    }
}
