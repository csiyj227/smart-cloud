package com.smart.codegen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.codegen.entity.GenTemplate;
import com.smart.codegen.entity.GenTemplateGroup;
import com.smart.codegen.service.GenTemplateGroupService;
import com.smart.codegen.service.GenTemplateService;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/codegen/template")
@RequiredArgsConstructor
public class GenTemplateController {

    private final GenTemplateService genTemplateService;
    private final GenTemplateGroupService genTemplateGroupService;

    @GetMapping("/group/page")
    public ApiResult<Page<GenTemplateGroup>> groupPage(Page<GenTemplateGroup> page) {
        return ApiResult.success(genTemplateGroupService.page(page));
    }

    @GetMapping("/group/list")
    public ApiResult<List<GenTemplateGroup>> groupList() {
        return ApiResult.success(genTemplateGroupService.list());
    }

    @PostMapping("/group")
    public ApiResult<Void> saveGroup(@RequestBody GenTemplateGroup group) {
        genTemplateGroupService.save(group);
        return ApiResult.success();
    }

    @PutMapping("/group")
    public ApiResult<Void> updateGroup(@RequestBody GenTemplateGroup group) {
        genTemplateGroupService.updateById(group);
        return ApiResult.success();
    }

    @DeleteMapping("/group/{id}")
    public ApiResult<Void> deleteGroup(@PathVariable Long id) {
        genTemplateGroupService.removeById(id);
        return ApiResult.success();
    }

    @GetMapping("/list")
    public ApiResult<List<GenTemplate>> listByGroup(@RequestParam Long groupId) {
        return ApiResult.success(genTemplateService.list(new LambdaQueryWrapper<GenTemplate>()
                .eq(GenTemplate::getGroupId, groupId)
                .orderByAsc(GenTemplate::getSortOrder)));
    }

    @GetMapping("/{id}")
    public ApiResult<GenTemplate> getById(@PathVariable Long id) {
        return ApiResult.success(genTemplateService.getById(id));
    }

    @PostMapping
    public ApiResult<Void> save(@RequestBody GenTemplate template) {
        genTemplateService.save(template);
        return ApiResult.success();
    }

    @PutMapping
    public ApiResult<Void> update(@RequestBody GenTemplate template) {
        genTemplateService.updateById(template);
        return ApiResult.success();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        genTemplateService.removeById(id);
        return ApiResult.success();
    }
}