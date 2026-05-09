package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.entity.BizTravelApply;
import com.smart.admin.service.BizTravelApplyService;
import com.smart.common.core.web.ApiResult;
import com.smart.common.security.component.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 出差申请单 Controller。
 *
 * <p>接口路径 /travel-apply，供前端出差申请列表 + 表单页面调用。
 * 同时也是自定义表单关联流程时的后端接口。
 */
@RestController
@RequestMapping("/system/travel-apply")
@RequiredArgsConstructor
public class BizTravelApplyController {

    private final BizTravelApplyService travelApplyService;
    private final PermissionEvaluator permissionEvaluator;

    @PreAuthorize("@authz.hasPermission('biz_travel_view')")
    @GetMapping("/page")
    public ApiResult<Page<BizTravelApply>> page(Page<BizTravelApply> page, BizTravelApply query) {
        return ApiResult.success(travelApplyService.page(page, Wrappers.<BizTravelApply>lambdaQuery()
                .like(query.getApplyNo() != null && !query.getApplyNo().isEmpty(),
                        BizTravelApply::getApplyNo, query.getApplyNo())
                .like(query.getApplicantName() != null && !query.getApplicantName().isEmpty(),
                        BizTravelApply::getApplicantName, query.getApplicantName())
                .like(query.getDestination() != null && !query.getDestination().isEmpty(),
                        BizTravelApply::getDestination, query.getDestination())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(),
                        BizTravelApply::getStatus, query.getStatus())
                .orderByDesc(BizTravelApply::getCreateTime)));
    }

    @PreAuthorize("@authz.hasPermission('biz_travel_view')")
    @GetMapping("/{applyId}")
    public ApiResult<BizTravelApply> getById(@PathVariable Long applyId) {
        return ApiResult.success(travelApplyService.getById(applyId));
    }

    @PreAuthorize("@authz.hasPermission('biz_travel_add')")
    @PostMapping
    public ApiResult<BizTravelApply> save(@RequestBody BizTravelApply apply) {
        // 自动生成申请单号
        if (apply.getApplyNo() == null || apply.getApplyNo().isEmpty()) {
            apply.setApplyNo(generateApplyNo());
        }
        // 填充申请人信息
        Long currentUserId = permissionEvaluator.getCurrentUserId();
        String currentUserName = permissionEvaluator.getCurrentUserName();
        apply.setApplicantId(currentUserId);
        apply.setApplicantName(currentUserName);
        // 默认草稿状态
        if (apply.getStatus() == null || apply.getStatus().isEmpty()) {
            apply.setStatus("DRAFT");
        }
        travelApplyService.save(apply);
        return ApiResult.success(apply);
    }

    @PreAuthorize("@authz.hasPermission('biz_travel_edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody BizTravelApply apply) {
        travelApplyService.updateById(apply);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('biz_travel_del')")
    @DeleteMapping("/{applyId}")
    public ApiResult<Void> delete(@PathVariable Long applyId) {
        travelApplyService.removeById(applyId);
        return ApiResult.success();
    }

    /**
     * 提交审批：将出差申请单状态改为 PENDING，并返回表单数据供流程引擎使用。
     */
    @PreAuthorize("@authz.hasPermission('biz_travel_submit')")
    @PostMapping("/{applyId}/submit")
    public ApiResult<BizTravelApply> submit(@PathVariable Long applyId) {
        BizTravelApply apply = travelApplyService.getById(applyId);
        if (apply == null) {
            return ApiResult.failure("申请单不存在");
        }
        if (!"DRAFT".equals(apply.getStatus()) && !"REJECTED".equals(apply.getStatus())) {
            return ApiResult.failure("仅草稿或已驳回状态可提交");
        }
        apply.setStatus("PENDING");
        travelApplyService.updateById(apply);
        return ApiResult.success(apply);
    }

    /**
     * 流程回调：审批通过后由流程引擎回调更新状态。
     */
    @PutMapping("/{applyId}/approve")
    public ApiResult<Void> approve(@PathVariable Long applyId) {
        BizTravelApply apply = travelApplyService.getById(applyId);
        if (apply != null) {
            apply.setStatus("APPROVED");
            travelApplyService.updateById(apply);
        }
        return ApiResult.success();
    }

    /**
     * 绑定流程实例：提交审批发起流程后，将 processInstanceId 回写到出差申请单。
     */
    @PreAuthorize("@authz.hasPermission('biz_travel_submit')")
    @PutMapping("/{applyId}/bindProcess")
    public ApiResult<Void> bindProcess(@PathVariable Long applyId, @RequestBody java.util.Map<String, String> body) {
        String processInstanceId = body.get("processInstanceId");
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            return ApiResult.failure("processInstanceId 不能为空");
        }
        BizTravelApply apply = travelApplyService.getById(applyId);
        if (apply == null) {
            return ApiResult.failure("申请单不存在");
        }
        apply.setProcessInstanceId(processInstanceId);
        apply.setStatus("PENDING");
        travelApplyService.updateById(apply);
        return ApiResult.success();
    }

    /**
     * 流程回调：审批驳回后由流程引擎回调更新状态。
     */
    @PutMapping("/{applyId}/reject")
    public ApiResult<Void> reject(@PathVariable Long applyId) {
        BizTravelApply apply = travelApplyService.getById(applyId);
        if (apply != null) {
            apply.setStatus("REJECTED");
            travelApplyService.updateById(apply);
        }
        return ApiResult.success();
    }

    private String generateApplyNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "TRAVEL-" + datePart + "-" + randomPart;
    }
}
