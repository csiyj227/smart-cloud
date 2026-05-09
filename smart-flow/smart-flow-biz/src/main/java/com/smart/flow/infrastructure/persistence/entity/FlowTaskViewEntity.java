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
 * Persistence model of {@code flow_task_view} - the denormalised CQRS read model that backs
 * every page of the task center.
 *
 * <p>One row per (task_id, candidate_user_id) tuple:
 * <ul>
 *   <li>for an assignee-only task exactly one row exists (assignee = candidate);</li>
 *   <li>for a candidate-group task one row is materialised per resolved candidate user, all
 *       sharing the same {@code task_id}; whoever claims first transitions all sibling rows
 *       to {@code claimed} so the others stop seeing it in their pending list.</li>
 * </ul>
 *
 * <p>The row is created/updated by the {@code TaskViewProjector} in response to domain
 * events; query services treat it as read-only.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_task_view")
@TenantEntity
public class FlowTaskViewEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long viewId;

    private String taskId;
    private String processInstanceId;
    private String chartKey;
    private String chartName;
    private String bizNo;
    private String title;
    private String nodeKey;
    private String nodeName;
    private Long candidateUserId;
    private String candidateUserName;
    private Long starterId;
    private String starterName;

    /** {@code pending} | {@code claimed} | {@code completed} | {@code withdrawn} | {@code terminated}. */
    private String viewStatus;

    private LocalDateTime receivedAt;
    private LocalDateTime finishedAt;

    /** Cached form id so the listing endpoint can render an entry-point button. */
    private Long formId;
}
