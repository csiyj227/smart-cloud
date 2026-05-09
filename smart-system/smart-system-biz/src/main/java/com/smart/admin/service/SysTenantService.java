package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysTenant;

public interface SysTenantService extends IService<SysTenant> {

    SysTenant findByCode(String tenantCode);
}