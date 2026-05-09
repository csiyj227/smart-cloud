package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.api.dto.SysLogDTO;
import com.smart.admin.entity.SysLog;
import com.smart.admin.mapper.SysLogMapper;
import com.smart.admin.service.SysLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService {

    @Override
    public void saveLog(SysLogDTO logDTO) {
        if (logDTO == null) {
            return;
        }
        SysLog entity = new SysLog();
        BeanUtils.copyProperties(logDTO, entity);
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(OffsetDateTime.now(ZoneOffset.UTC));
        }
        save(entity);
    }

    @Override
    public int clearBefore(int days) {
        if (days <= 0) {
            return 0;
        }
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        QueryWrapper<SysLog> wrapper = new QueryWrapper<>();
        wrapper.lt("create_time", threshold);
        int affected = baseMapper.delete(wrapper);
        log.info("Cleared {} sys_log records older than {} days", affected, days);
        return affected;
    }
}