package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysDept;
import com.smart.admin.mapper.SysDeptMapper;
import com.smart.admin.service.SysDeptService;
import com.smart.common.core.enums.StatusFlag;
import com.smart.common.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    @Override
    public List<SysDept> buildDeptTree(Long tenantId) {
        List<SysDept> allDepts = list(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getTenantId, tenantId)
                .eq(SysDept::getDelFlag, StatusFlag.NOT_DELETED.getValue())
                .orderByAsc(SysDept::getSortOrder));

        // Build parent -> children map
        java.util.Map<Long, List<SysDept>> childrenMap = new java.util.HashMap<>();
        for (SysDept dept : allDepts) {
            childrenMap.computeIfAbsent(dept.getParentId(), k -> new ArrayList<>()).add(dept);
        }

        // Attach children to each dept
        for (SysDept dept : allDepts) {
            dept.setChildren(childrenMap.get(dept.getDeptId()));
        }

        // Return only root nodes (parentId == 0)
        return allDepts.stream()
                .filter(d -> d.getParentId() == null || d.getParentId() == 0L)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> findDeptAndChildIds(Long deptId) {
        SysDept dept = getById(deptId);
        if (dept == null) {
            return List.of(deptId);
        }
        List<SysDept> children = list(new LambdaQueryWrapper<SysDept>()
                .likeRight(SysDept::getAncestors, dept.getAncestors() + "," + deptId)
                .eq(SysDept::getDelFlag, StatusFlag.NOT_DELETED.getValue()));
        List<Long> ids = new ArrayList<>();
        ids.add(deptId);
        ids.addAll(children.stream().map(SysDept::getDeptId).collect(Collectors.toList()));
        return ids;
    }
}