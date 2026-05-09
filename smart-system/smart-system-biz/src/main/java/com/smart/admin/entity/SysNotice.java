package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * System notice/announcement entity.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
@TenantEntity
public class SysNotice extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long noticeId;

    /**
     * Notice title
     */
    private String noticeTitle;

    /**
     * Notice type: 1=notice, 2=announcement
     */
    private String noticeType;

    /**
     * Notice content
     */
    private String noticeContent;

    /**
     * Publisher
     */
    private String publisher;

    /**
     * Priority: 1=low, 2=normal, 3=high
     */
    private String priority;

    /**
     * Status: 0=draft, 1=published, 2=archived
     */
    private String status;

    /**
     * Publish time
     */
    private LocalDateTime publishTime;

    /**
     * Expire time (optional)
     */
    private LocalDateTime expireTime;
}