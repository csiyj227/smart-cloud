package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysDict;

public interface SysDictService extends IService<SysDict> {

    SysDict findByTypeCode(String typeCode, Long tenantId);

    void deleteDictWithItems(Long id);
}