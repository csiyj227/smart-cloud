package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysTenant;
import com.smart.admin.mapper.SysTenantMapper;
import com.smart.admin.service.SysTenantService;
import org.springframework.stereotype.Service;

@Service
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant> implements SysTenantService {

    @Override
    public SysTenant findByCode(String tenantCode) {
        return getOne(new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getTenantCode, tenantCode));
    }
}