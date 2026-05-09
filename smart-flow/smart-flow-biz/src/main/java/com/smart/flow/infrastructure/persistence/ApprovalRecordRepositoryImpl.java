package com.smart.flow.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.flow.domain.audit.ApprovalRecordRepository;
import com.smart.flow.infrastructure.persistence.entity.FlowApprovalRecordEntity;
import com.smart.flow.infrastructure.persistence.mapper.FlowApprovalRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ApprovalRecordRepositoryImpl implements ApprovalRecordRepository {

    private final FlowApprovalRecordMapper mapper;

    @Override
    public void append(FlowApprovalRecordEntity entity) {
        if (entity.getOccurredAt() == null) {
            entity.setOccurredAt(LocalDateTime.now());
        }
        mapper.insert(entity);
    }

    @Override
    public List<FlowApprovalRecordEntity> findByProcessInstanceId(String processInstanceId) {
        return mapper.selectList(new LambdaQueryWrapper<FlowApprovalRecordEntity>()
                .eq(FlowApprovalRecordEntity::getProcessInstanceId, processInstanceId)
                .orderByAsc(FlowApprovalRecordEntity::getOccurredAt));
    }
}
