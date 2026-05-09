package com.smart.flow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Persistence model of {@code flow_instance_biz} - the business-side companion of a Flowable
 * process instance.
 *
 * <p>The {@code processInstanceId} column is unique and acts as the bridge to the {@code act_*}
 * tables; the rest of the columns are denormalised so that listing pages can render without
 * touching Flowable history at all.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_instance_biz")
@TenantEntity
public class FlowInstanceBizEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long bizId;

    /** Flowable runtime / history process-instance id. Unique per row. */
    private String processInstanceId;

    private Long chartId;
    private String chartKey;
    private Integer chartVersion;

    /** Human-friendly serial like "LV-20260502-000123". */
    private String bizNo;

    private String title;
    private Long starterId;
    private String starterName;
    private Long starterDeptId;

    /** "0" running, "1" approved, "2" rejected, "3" withdrawn, "4" terminated. */
    private String bizStatus;

    private Long latestSnapshotId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
}
