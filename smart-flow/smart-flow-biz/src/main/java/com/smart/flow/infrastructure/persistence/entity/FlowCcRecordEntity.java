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
 * Persistence model of {@code flow_cc_record}.
 *
 * <p>A CC entry is intentionally <em>not</em> a Flowable task - it has no completion
 * semantics, only "viewed" / "not viewed". This keeps the engine's runtime tables small (CCs
 * can fan out to dozens of users) while still letting the task center surface them alongside
 * real to-dos.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_cc_record")
@TenantEntity
public class FlowCcRecordEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long ccId;

    private String processInstanceId;
    private String nodeKey;
    private String nodeName;
    private Long ccUserId;
    private String ccUserName;
    private Long sentBy;
    private LocalDateTime sentAt;

    /** "0" unread, "1" read. */
    private String readFlag;

    private LocalDateTime readAt;
}
