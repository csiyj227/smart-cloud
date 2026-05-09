package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysRouteConf;
import com.smart.admin.mapper.SysRouteConfMapper;
import com.smart.admin.service.SysRouteConfService;
import com.smart.common.core.enums.StatusFlag;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysRouteConfServiceImpl extends ServiceImpl<SysRouteConfMapper, SysRouteConf> implements SysRouteConfService {

    @Override
    public List<SysRouteConf> findAllActiveRoutes() {
        return list(new LambdaQueryWrapper<SysRouteConf>()
                .eq(SysRouteConf::getStatus, StatusFlag.NOT_DELETED.getValue()));
    }
}