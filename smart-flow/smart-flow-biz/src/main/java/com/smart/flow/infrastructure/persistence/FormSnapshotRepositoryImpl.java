package com.smart.flow.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.flow.domain.form.FormSnapshotRepository;
import com.smart.flow.infrastructure.persistence.entity.FlowFormSnapshotEntity;
import com.smart.flow.infrastructure.persistence.mapper.FlowFormSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FormSnapshotRepositoryImpl implements FormSnapshotRepository {

    private final FlowFormSnapshotMapper mapper;

    @Override
    public void append(FlowFormSnapshotEntity snapshot) {
        mapper.insert(snapshot);
    }

    @Override
    public List<FlowFormSnapshotEntity> listByProcessInstance(String processInstanceId) {
        return mapper.selectList(new LambdaQueryWrapper<FlowFormSnapshotEntity>()
                .eq(FlowFormSnapshotEntity::getProcessInstanceId, processInstanceId)
                .orderByAsc(FlowFormSnapshotEntity::getCapturedAt));
    }

    @Override
    public Optional<FlowFormSnapshotEntity> findLatest(String processInstanceId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<FlowFormSnapshotEntity>()
                .eq(FlowFormSnapshotEntity::getProcessInstanceId, processInstanceId)
                .orderByDesc(FlowFormSnapshotEntity::getCapturedAt)
                .last("LIMIT 1")));
    }
}
