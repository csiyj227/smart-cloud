package com.smart.flow.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.flow.domain.instance.FlowInstanceRepository;
import com.smart.flow.infrastructure.persistence.entity.FlowInstanceBizEntity;
import com.smart.flow.infrastructure.persistence.mapper.FlowInstanceBizMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FlowInstanceRepositoryImpl implements FlowInstanceRepository {

    private final FlowInstanceBizMapper mapper;

    @Override
    public FlowInstanceBizEntity save(FlowInstanceBizEntity entity) {
        if (entity.getBizId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return entity;
    }

    @Override
    public Optional<FlowInstanceBizEntity> findById(Long bizId) {
        return Optional.ofNullable(mapper.selectById(bizId));
    }

    @Override
    public Optional<FlowInstanceBizEntity> findByProcessInstanceId(String processInstanceId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<FlowInstanceBizEntity>()
                .eq(FlowInstanceBizEntity::getProcessInstanceId, processInstanceId)
                .last("LIMIT 1")));
    }
}
