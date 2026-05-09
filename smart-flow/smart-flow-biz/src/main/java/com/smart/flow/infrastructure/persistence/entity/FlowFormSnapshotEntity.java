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
 * Persistence model of {@code flow_form_snapshot}.
 *
 * <p>Each approval action captures the form payload as it stood at that moment; this lets the
 * audit trail show exactly what each approver saw and submitted, even if the form schema is
 * later edited. The {@code payload} column holds the entire JSON blob - heavy by design but
 * snapshots are read rarely (only when an auditor opens the timeline).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_form_snapshot")
@TenantEntity
public class FlowFormSnapshotEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long snapshotId;

    private String processInstanceId;
    private String taskId;
    private String nodeKey;
    private Long formId;

    /** "0" starter, "1" approval, "2" sign-off, "3" system patch. */
    private String snapshotType;

    /** Full form payload (JSON). */
    private String payload;

    private Long capturedBy;
    private LocalDateTime capturedAt;
}
