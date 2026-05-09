package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysDept;

import java.util.List;

public interface SysDeptService extends IService<SysDept> {

    List<SysDept> buildDeptTree(Long tenantId);

    List<Long> findDeptAndChildIds(Long deptId);
}