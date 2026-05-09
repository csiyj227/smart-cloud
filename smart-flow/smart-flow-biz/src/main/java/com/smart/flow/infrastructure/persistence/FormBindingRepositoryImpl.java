package com.smart.flow.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.flow.domain.form.FormBindingRepository;
import com.smart.flow.infrastructure.persistence.entity.FlowFormBindingEntity;
import com.smart.flow.infrastructure.persistence.mapper.FlowFormBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus adapter for {@link FormBindingRepository}.
 *
 * <p>{@code upsert} is implemented as "find existing by (chart_id, node_key) and update,
 * otherwise insert" rather than relying on database-specific upsert syntax. This keeps
 * the repository portable across PostgreSQL / MySQL.
 */
@Repository
@RequiredArgsConstructor
public class FormBindingRepositoryImpl implements FormBindingRepository {

    private final FlowFormBindingMapper mapper;

    @Override
    public void upsert(FlowFormBindingEntity entity) {
        Optional<FlowFormBindingEntity> existing = entity.getNodeKey() == null
                ? findChartLevel(entity.getChartId())
                : findNodeLevel(entity.getChartId(), entity.getNodeKey());
        if (existing.isPresent()) {
            entity.setBindingId(existing.get().getBindingId());
            mapper.updateById(entity);
        } else {
            mapper.insert(entity);
        }
    }

    @Override
    public void deleteById(Long bindingId) {
        if (bindingId == null) {
            return;
        }
        mapper.deleteById(bindingId);
    }

    @Override
    public Optional<FlowFormBindingEntity> findById(Long bindingId) {
        return Optional.ofNullable(mapper.selectById(bindingId));
    }

    @Override
    public Optional<FlowFormBindingEntity> findChartLevel(Long chartId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<FlowFormBindingEntity>()
                .eq(FlowFormBindingEntity::getChartId, chartId)
                .isNull(FlowFormBindingEntity::getNodeKey)
                .last("LIMIT 1")));
    }

    @Override
    public Optional<FlowFormBindingEntity> findNodeLevel(Long chartId, String nodeKey) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<FlowFormBindingEntity>()
                .eq(FlowFormBindingEntity::getChartId, chartId)
                .eq(FlowFormBindingEntity::getNodeKey, nodeKey)
                .last("LIMIT 1")));
    }

    @Override
    public List<FlowFormBindingEntity> listByChart(Long chartId) {
        return mapper.selectList(new LambdaQueryWrapper<FlowFormBindingEntity>()
                .eq(FlowFormBindingEntity::getChartId, chartId)
                .orderByAsc(FlowFormBindingEntity::getNodeKey));
    }
}
