package com.smart.admin.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务依赖关系：A 完成后触发 B。
 * jobId 表示"被触发方"，dependsOnJobId 表示"触发源"。
 */
@Data
@TableName("sys_job_dep")
public class SysJobDep implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long jobId;
    private Long dependsOnJobId;
    private Long tenantId;
}
