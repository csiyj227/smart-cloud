package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.entity.SysForm;
import com.smart.admin.entity.SysFormData;
import com.smart.admin.service.SysFormService;
import com.smart.admin.support.JwtClaimUtils;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/system/form")
@RequiredArgsConstructor
public class SysFormController {

    private final SysFormService sysFormService;

    @PreAuthorize("@authz.hasPermission('sys_form_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysForm>> page(Page<SysForm> page, SysForm query,
                                  @RequestParam(required = false) String keyword) {
        return ApiResult.success(sysFormService.page(page, Wrappers.<SysForm>lambdaQuery()
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(SysForm::getFormName, keyword)
                        .or()
                        .like(SysForm::getFormKey, keyword))
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), SysForm::getStatus, query.getStatus())
                .eq(query.getCategory() != null && !query.getCategory().isEmpty(), SysForm::getCategory, query.getCategory())
                .orderByDesc(SysForm::getUpdateTime)));
    }

    @GetMapping("/{formId}")
    public ApiResult<SysForm> getById(@PathVariable Long formId) {
        return ApiResult.success(sysFormService.getById(formId));
    }

    @GetMapping("/key/{formKey}")
    public ApiResult<SysForm> getByKey(@PathVariable String formKey) {
        return ApiResult.success(sysFormService.getFormByKey(formKey));
    }

    @PreAuthorize("@authz.hasPermission('sys_form_add')")
    @PostMapping
    public ApiResult<Long> create(@RequestBody SysForm form) {
        sysFormService.save(form);
        return ApiResult.success(form.getFormId());
    }

    @PreAuthorize("@authz.hasPermission('sys_form_edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysForm form) {
        sysFormService.updateById(form);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_form_del')")
    @DeleteMapping("/{formId}")
    public ApiResult<Void> delete(@PathVariable Long formId) {
        sysFormService.removeById(formId);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_form_edit')")
    @PostMapping("/{formId}/publish")
    public ApiResult<Void> publish(@PathVariable Long formId) {
        sysFormService.publishForm(formId);
        return ApiResult.success();
    }

    @PostMapping("/data")
    public ApiResult<Void> submitData(@RequestBody Map<String, Object> params, Authentication authentication) {
        Long formId = Long.parseLong(params.get("formId").toString());
        String formKey = params.get("formKey").toString();
        String formData = params.get("formData").toString();

        sysFormService.submitFormData(formId, formKey, JwtClaimUtils.getLong(authentication, "user_id"), formData,
                "unknown", "unknown");
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_form_view')")
    @GetMapping("/{formId}/data")
    public ApiResult<List<SysFormData>> getFormDataList(@PathVariable Long formId) {
        return ApiResult.success(sysFormService.getFormDataList(formId));
    }

    @GetMapping("/{formId}/my-data")
    public ApiResult<List<SysFormData>> getMyFormData(@PathVariable Long formId, Authentication authentication) {
        return ApiResult.success(sysFormService.getUserFormData(formId, JwtClaimUtils.getLong(authentication, "user_id")));
    }
}
