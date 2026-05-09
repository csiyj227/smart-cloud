package com.smart.admin.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job_log")
public class SysJobLog extends AuditableEntity {

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    private Long jobId;
    private String jobName;
    private String jobGroup;
    private String invokeTarget;
    private String jobParam;
    /** CRON / MANUAL / DEPENDENCY */
    private String triggerType;
    /** 0=成功 1=失败 */
    private String status;
    private String exceptionInfo;
    private String result;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private Long tenantId;
}
