package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.entity.SysNotice;
import com.smart.admin.entity.SysNoticeRead;
import com.smart.admin.service.SysNoticeService;
import com.smart.admin.support.JwtClaimUtils;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeService sysNoticeService;

    @PreAuthorize("@authz.hasPermission('sys_notice_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysNotice>> page(Page<SysNotice> page, SysNotice query) {
        return ApiResult.success(sysNoticeService.page(page, Wrappers.<SysNotice>lambdaQuery()
                .like(query.getNoticeTitle() != null && !query.getNoticeTitle().isEmpty(), SysNotice::getNoticeTitle, query.getNoticeTitle())
                .eq(query.getNoticeType() != null && !query.getNoticeType().isEmpty(), SysNotice::getNoticeType, query.getNoticeType())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), SysNotice::getStatus, query.getStatus())
                .orderByDesc(SysNotice::getCreateTime)));
    }

    @GetMapping("/{noticeId}")
    public ApiResult<SysNotice> getById(@PathVariable Long noticeId) {
        return ApiResult.success(sysNoticeService.getById(noticeId));
    }

    @PreAuthorize("@authz.hasPermission('sys_notice_add')")
    @PostMapping
    public ApiResult<Void> publish(@RequestBody SysNotice notice) {
        sysNoticeService.publish(notice);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_notice_edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysNotice notice) {
        sysNoticeService.updateById(notice);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_notice_del')")
    @DeleteMapping("/{noticeId}")
    public ApiResult<Void> delete(@PathVariable Long noticeId) {
        sysNoticeService.removeById(noticeId);
        return ApiResult.success();
    }

    @GetMapping("/list")
    public ApiResult<List<SysNotice>> list() {
        return ApiResult.success(sysNoticeService.getPublishedNotices());
    }

    @PostMapping("/{noticeId}/read")
    public ApiResult<Void> markAsRead(@PathVariable Long noticeId, Authentication authentication) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        sysNoticeService.markAsRead(noticeId, userId);
        return ApiResult.success();
    }

    @GetMapping("/unread-count")
    public ApiResult<Long> getUnreadCount(Authentication authentication) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        return ApiResult.success(sysNoticeService.getUnreadCount(userId));
    }

    @PreAuthorize("@authz.hasPermission('sys_notice_view')")
    @GetMapping("/{noticeId}/reads")
    public ApiResult<List<SysNoticeRead>> getReadRecords(@PathVariable Long noticeId) {
        return ApiResult.success(sysNoticeService.getReadRecords(noticeId));
    }
}
