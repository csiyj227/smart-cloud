package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysRouteConf;

import java.util.List;

public interface SysRouteConfService extends IService<SysRouteConf> {

    List<SysRouteConf> findAllActiveRoutes();
}