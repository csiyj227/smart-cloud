package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出差申请单实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_travel_apply")
@TenantEntity
public class BizTravelApply extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long applyId;

    /** 申请单号 */
    private String applyNo;

    /** 申请人 ID */
    private Long applicantId;

    /** 申请人姓名 */
    private String applicantName;

    /** 部门名称 */
    private String deptName;

    /** 出差事由 */
    private String reason;

    /** 出发地 */
    private String departure;

    /** 目的地 */
    private String destination;

    /** 出发时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /** 返回时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    /** 交通方式 */
    private String transport;

    /** 预计费用 */
    private BigDecimal estimatedCost;

    /** 备注 */
    private String remark;

    /** 状态：DRAFT/PENDING/APPROVED/REJECTED */
    private String status;

    /** 关联的流程实例 ID */
    private String processInstanceId;

    /** 租户 ID */
    private Long tenantId;
}
