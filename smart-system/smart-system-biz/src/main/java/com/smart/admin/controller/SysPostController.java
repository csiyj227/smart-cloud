package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.entity.SysPost;
import com.smart.admin.service.SysPostService;
import com.smart.common.core.web.ApiResult;
import com.smart.common.log.annotation.AuditTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/post")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostService sysPostService;

    @PreAuthorize("@authz.hasPermission('sys_post_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysPost>> page(Page<SysPost> page, SysPost query) {
        return ApiResult.success(sysPostService.page(page, Wrappers.<SysPost>lambdaQuery()
                .like(query.getPostCode() != null && !query.getPostCode().isEmpty(), SysPost::getPostCode, query.getPostCode())
                .like(query.getPostName() != null && !query.getPostName().isEmpty(), SysPost::getPostName, query.getPostName())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), SysPost::getStatus, query.getStatus())));
    }

    /**
     * 查询全部岗位列表（用于下拉选择等场景，不分页）
     */
    @GetMapping("/list")
    public ApiResult<java.util.List<SysPost>> list(SysPost query) {
        return ApiResult.success(sysPostService.list(Wrappers.<SysPost>lambdaQuery()
                .like(query.getPostCode() != null && !query.getPostCode().isEmpty(), SysPost::getPostCode, query.getPostCode())
                .like(query.getPostName() != null && !query.getPostName().isEmpty(), SysPost::getPostName, query.getPostName())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), SysPost::getStatus, query.getStatus())));
    }

    @PreAuthorize("@authz.hasPermission('sys_post_view')")
    @GetMapping("/{postId}")
    public ApiResult<SysPost> getById(@PathVariable Long postId) {
        return ApiResult.success(sysPostService.getById(postId));
    }

    @PreAuthorize("@authz.hasPermission('sys_post_add')")
    @AuditTrace("新增岗位")
    @PostMapping
    public ApiResult<Void> save(@RequestBody SysPost post) {
        sysPostService.save(post);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_post_edit')")
    @AuditTrace("修改岗位")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysPost post) {
        sysPostService.updateById(post);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_post_del')")
    @AuditTrace("删除岗位")
    @DeleteMapping("/{postId}")
    public ApiResult<Void> delete(@PathVariable Long postId) {
        sysPostService.removeById(postId);
        return ApiResult.success();
    }
}