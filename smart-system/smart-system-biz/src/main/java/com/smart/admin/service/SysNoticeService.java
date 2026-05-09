package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysNotice;
import com.smart.admin.entity.SysNoticeRead;

import java.util.List;

/**
 * Notice service interface.
 */
public interface SysNoticeService extends IService<SysNotice> {

    /**
     * Publish a notice.
     */
    void publish(SysNotice notice);

    /**
     * Get published notices for current user.
     */
    List<SysNotice> getPublishedNotices();

    /**
     * Mark notice as read.
     */
    void markAsRead(Long noticeId, Long userId);

    /**
     * Get unread notice count for user.
     */
    long getUnreadCount(Long userId);

    /**
     * Get notice read records.
     */
    List<SysNoticeRead> getReadRecords(Long noticeId);
}