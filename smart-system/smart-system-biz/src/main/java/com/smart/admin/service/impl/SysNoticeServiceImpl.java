package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysNotice;
import com.smart.admin.entity.SysNoticeRead;
import com.smart.admin.mapper.SysNoticeMapper;
import com.smart.admin.mapper.SysNoticeReadMapper;
import com.smart.admin.service.SysNoticeService;
import com.smart.common.core.enums.StatusFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notice service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice>
        implements SysNoticeService {

    private final SysNoticeReadMapper noticeReadMapper;

    @Override
    @Transactional
    public void publish(SysNotice notice) {
        notice.setStatus(StatusFlag.ENABLED.getValue());
        notice.setPublishTime(LocalDateTime.now());
        save(notice);
        log.debug("Published notice: {}", notice.getNoticeId());
    }

    @Override
    public List<SysNotice> getPublishedNotices() {
        return list(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, StatusFlag.ENABLED.getValue())
                .le(SysNotice::getPublishTime, LocalDateTime.now())
                .and(w -> w.isNull(SysNotice::getExpireTime)
                        .or().ge(SysNotice::getExpireTime, LocalDateTime.now()))
                .orderByDesc(SysNotice::getPublishTime));
    }

    @Override
    @Transactional
    public void markAsRead(Long noticeId, Long userId) {
        SysNoticeRead record = new SysNoticeRead();
        record.setNoticeId(noticeId);
        record.setUserId(userId);
        record.setReadTime(LocalDateTime.now());
        record.setStatus(StatusFlag.ENABLED.getValue());

        // Check if already read
        SysNoticeRead existing = noticeReadMapper.selectOne(
                new LambdaQueryWrapper<SysNoticeRead>()
                        .eq(SysNoticeRead::getNoticeId, noticeId)
                        .eq(SysNoticeRead::getUserId, userId)
        );

        if (existing == null) {
            noticeReadMapper.insert(record);
        } else {
            existing.setReadTime(LocalDateTime.now());
            existing.setStatus(StatusFlag.ENABLED.getValue());
            noticeReadMapper.updateById(existing);
        }
        log.debug("Marked notice {} as read by user {}", noticeId, userId);
    }

    @Override
    public long getUnreadCount(Long userId) {
        // Get all published notices
        List<SysNotice> notices = getPublishedNotices();
        if (notices.isEmpty()) {
            return 0;
        }

        // Count notices not in read records
        List<Long> noticeIds = notices.stream().map(SysNotice::getNoticeId).toList();

        long readCount = noticeReadMapper.selectCount(
                new LambdaQueryWrapper<SysNoticeRead>()
                        .eq(SysNoticeRead::getUserId, userId)
                        .eq(SysNoticeRead::getStatus, StatusFlag.ENABLED.getValue())
                        .in(SysNoticeRead::getNoticeId, noticeIds)
        );

        return notices.size() - readCount;
    }

    @Override
    public List<SysNoticeRead> getReadRecords(Long noticeId) {
        return noticeReadMapper.selectList(
                new LambdaQueryWrapper<SysNoticeRead>()
                        .eq(SysNoticeRead::getNoticeId, noticeId)
                        .orderByDesc(SysNoticeRead::getReadTime)
        );
    }
}