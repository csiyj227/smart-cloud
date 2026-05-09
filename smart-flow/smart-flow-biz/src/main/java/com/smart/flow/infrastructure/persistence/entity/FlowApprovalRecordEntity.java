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
 * Append-only audit row in {@code flow_approval_record}. One row per user-visible action; the
 * timeline UI is built from a paginated query against this table sorted by {@code occurredAt}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_approval_record")
@TenantEntity
public class FlowApprovalRecordEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long recordId;

    private String processInstanceId;
    private String taskId;
    private String nodeKey;
    private String nodeName;

    /** One of the {@code com.smart.flow.api.dsl.ApprovalAction} wire codes. */
    private String actionType;

    private Long actorId;
    private String actorName;

    /** Recipient for transfer / delegate / addSign actions. */
    private Long targetUserId;
    private String targetUserName;

    private String comment;

    /** JSON array of attachment metadata; deliberately stored as raw text to avoid coupling. */
    private String attachments;

    private LocalDateTime occurredAt;
}
