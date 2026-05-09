package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Notice read record entity.
 * Tracks which users have read which notices.
 */
@Data
@TableName("sys_notice_read")
@TenantEntity
public class SysNoticeRead {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Notice ID
     */
    private Long noticeId;

    /**
     * User ID
     */
    private Long userId;

    /**
     * Read time
     */
    private LocalDateTime readTime;

    /**
     * Read status: 0=unread, 1=read
     */
    private String status;
}