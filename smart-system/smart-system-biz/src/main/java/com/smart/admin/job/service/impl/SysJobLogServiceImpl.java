package com.smart.admin.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.job.entity.SysJobLog;
import com.smart.admin.job.mapper.SysJobLogMapper;
import com.smart.admin.job.service.SysJobLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SysJobLogServiceImpl extends ServiceImpl<SysJobLogMapper, SysJobLog> implements SysJobLogService {

    @Override
    public IPage<SysJobLog> pageLog(Page<SysJobLog> page, Long jobId, String status) {
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<SysJobLog>()
                .eq(jobId != null, SysJobLog::getJobId, jobId)
                .eq(status != null && !status.isBlank(), SysJobLog::getStatus, status)
                .orderByDesc(SysJobLog::getLogId);
        return page(page, wrapper);
    }

    @Override
    public int cleanExpired(int days) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        return baseMapper.delete(new LambdaQueryWrapper<SysJobLog>()
                .lt(SysJobLog::getCreateTime, threshold));
    }
}
