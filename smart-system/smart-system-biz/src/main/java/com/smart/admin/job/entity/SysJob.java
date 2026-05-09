package com.smart.admin.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务配置。
 *
 * <p>{@code invokeTarget} 格式：
 * <pre>
 *   sysJobInternalTask.cleanLoginLog          // 无参
 *   sysJobInternalTask.cleanRecycleFile       // 无参
 *   userTask.process({"userId":1,"days":30})  // 内联 JSON 参数（也可放 jobParam 里）
 * </pre>
 *
 * <p>{@code jobParam} 优先级高于 {@code invokeTarget} 里的内联参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class SysJob extends AuditableEntity {

    @TableId(value = "job_id", type = IdType.AUTO)
    private Long jobId;

    private String jobName;
    private String jobGroup;
    private String invokeTarget;
    private String jobParam;
    private String cronExpression;
    /** 1=立即执行 2=执行一次 3=放弃 */
    private String misfirePolicy;
    /** 0=允许并发 1=禁止并发 */
    private String concurrent;
    /** 0=暂停 1=正常 */
    private String status;
    private Boolean alertOnFailure;
    private String alertUserIds;
    private String remark;
    private Long tenantId;
}
