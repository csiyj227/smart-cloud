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
 * Persistence model of {@code flow_delegation} - the long-term audit ledger for transfers
 * and delegations. Distinct from {@code act_ru_identitylink} (which Flowable wipes on task
 * completion) so that the timeline can still show "Bob transferred this to Alice" months
 * after the fact.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_delegation")
@TenantEntity
public class FlowDelegationEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long delegationId;

    private String taskId;
    private String processInstanceId;

    /** "transfer" = ownership permanently moves; "delegate" = temporary, returns on completion. */
    private String delegationType;

    private Long fromUserId;
    private String fromUserName;
    private Long toUserId;
    private String toUserName;

    private String reason;
    private LocalDateTime occurredAt;
}
