package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.api.dto.SysLogDTO;
import com.smart.admin.entity.SysLog;

public interface SysLogService extends IService<SysLog> {

    /**
     * 写入一条操作日志（来自 Feign 异步调用或本地 AOP）。
     */
    void saveLog(SysLogDTO logDTO);

    /**
     * 清理 N 天前的操作日志，返回受影响行数。
     */
    int clearBefore(int days);
}