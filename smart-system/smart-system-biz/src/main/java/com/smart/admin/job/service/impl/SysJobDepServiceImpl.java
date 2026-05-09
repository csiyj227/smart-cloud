package com.smart.admin.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.job.entity.SysJobDep;
import com.smart.admin.job.mapper.SysJobDepMapper;
import com.smart.admin.job.service.SysJobDepService;
import com.smart.admin.job.service.SysJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务依赖管理实现。
 *
 * <p>{@link #triggerDependents(Long)} 在 {@link com.smart.admin.job.quartz.SmartQuartzJob}
 * 成功执行后被回调，找出所有 dependsOnJobId 等于 sourceJobId 的下游任务，调用
 * {@code sysJobService.runOnce(downstreamJobId)} 立即触发。
 *
 * <p>注意：循环依赖在 {@link #resetUpstreams} 阶段做了简单防护（仅检测一层直接成环），
 * 多层环路依赖建议在前端编辑时校验或后续扩展拓扑排序检测。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysJobDepServiceImpl extends ServiceImpl<SysJobDepMapper, SysJobDep>
        implements SysJobDepService {

    /** Lazy 注入避免和 SysJobServiceImpl 之间循环依赖 */
    @Lazy
    private final SysJobService sysJobService;

    @Override
    public List<Long> listUpstreams(Long jobId) {
        return list(new LambdaQueryWrapper<SysJobDep>().eq(SysJobDep::getJobId, jobId))
                .stream().map(SysJobDep::getDependsOnJobId).toList();
    }

    @Override
    public List<Long> listDownstreams(Long jobId) {
        return list(new LambdaQueryWrapper<SysJobDep>().eq(SysJobDep::getDependsOnJobId, jobId))
                .stream().map(SysJobDep::getJobId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetUpstreams(Long jobId, List<Long> upstreamIds) {
        // 防直接成环：A→B 后又设 B→A
        if (upstreamIds != null) {
            for (Long up : upstreamIds) {
                if (up == null || up.equals(jobId)) continue;
                List<Long> upstreamOfUp = listUpstreams(up);
                if (upstreamOfUp.contains(jobId)) {
                    throw new com.smart.common.core.exception.BusinessException(
                            "Circular dependency detected: " + jobId + " <-> " + up);
                }
            }
        }
        remove(new LambdaQueryWrapper<SysJobDep>().eq(SysJobDep::getJobId, jobId));
        if (upstreamIds == null || upstreamIds.isEmpty()) return;
        List<SysJobDep> rows = new ArrayList<>(upstreamIds.size());
        for (Long up : upstreamIds) {
            if (up == null || up.equals(jobId)) continue;
            SysJobDep d = new SysJobDep();
            d.setJobId(jobId);
            d.setDependsOnJobId(up);
            rows.add(d);
        }
        if (!rows.isEmpty()) {
            saveBatch(rows);
        }
    }

    @Override
    public void triggerDependents(Long sourceJobId) {
        List<Long> downstreams = listDownstreams(sourceJobId);
        for (Long downId : downstreams) {
            try {
                sysJobService.runOnce(downId);
                log.info("Dependency triggered: {} -> {}", sourceJobId, downId);
            } catch (Exception e) {
                log.warn("Failed to trigger dependent job {}: {}", downId, e.getMessage());
            }
        }
    }
}
