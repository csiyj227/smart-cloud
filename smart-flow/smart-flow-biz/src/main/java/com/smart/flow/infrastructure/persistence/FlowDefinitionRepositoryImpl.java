package com.smart.flow.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smart.flow.domain.definition.FlowDefinitionRepository;
import com.smart.flow.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.smart.flow.infrastructure.persistence.mapper.FlowDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus backed implementation of {@link FlowDefinitionRepository}.
 *
 * <p>The implementation deliberately does not contain any caching - the cache is layered on
 * top of this class via Spring's {@code @Cacheable} on the application service, so this class
 * remains a thin, predictable persistence adapter.
 */
@Repository
@RequiredArgsConstructor
public class FlowDefinitionRepositoryImpl implements FlowDefinitionRepository {

    /** Wire constant for the {@code publish_status} column - kept here to avoid magic strings. */
    private static final String STATUS_DRAFT = "0";
    private static final String STATUS_PUBLISHED = "1";

    private final FlowDefinitionMapper mapper;

    @Override
    public FlowDefinitionEntity save(FlowDefinitionEntity entity) {
        if (entity.getChartId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return entity;
    }

    @Override
    public Optional<FlowDefinitionEntity> findById(Long chartId) {
        return Optional.ofNullable(mapper.selectById(chartId));
    }

    @Override
    public Optional<FlowDefinitionEntity> findByKeyAndVersion(String chartKey, Integer version) {
        LambdaQueryWrapper<FlowDefinitionEntity> wrapper = new LambdaQueryWrapper<FlowDefinitionEntity>()
                .eq(FlowDefinitionEntity::getChartKey, chartKey)
                .eq(FlowDefinitionEntity::getChartVersion, version)
                .last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public Optional<FlowDefinitionEntity> findLatestPublished(String chartKey) {
        return findLatestByStatus(chartKey, STATUS_PUBLISHED);
    }

    @Override
    public Optional<FlowDefinitionEntity> findLatestDraft(String chartKey) {
        return findLatestByStatus(chartKey, STATUS_DRAFT);
    }

    @Override
    public List<FlowDefinitionEntity> listByChartKey(String chartKey) {
        LambdaQueryWrapper<FlowDefinitionEntity> wrapper = new LambdaQueryWrapper<FlowDefinitionEntity>()
                .eq(FlowDefinitionEntity::getChartKey, chartKey)
                .orderByDesc(FlowDefinitionEntity::getChartVersion);
        return mapper.selectList(wrapper);
    }

    @Override
    public Integer nextVersion(String chartKey) {
        LambdaQueryWrapper<FlowDefinitionEntity> wrapper = new LambdaQueryWrapper<FlowDefinitionEntity>()
                .eq(FlowDefinitionEntity::getChartKey, chartKey)
                .orderByDesc(FlowDefinitionEntity::getChartVersion)
                .last("LIMIT 1");
        FlowDefinitionEntity latest = mapper.selectOne(wrapper);
        return latest == null ? 1 : latest.getChartVersion() + 1;
    }

    private Optional<FlowDefinitionEntity> findLatestByStatus(String chartKey, String status) {
        LambdaQueryWrapper<FlowDefinitionEntity> wrapper = new LambdaQueryWrapper<FlowDefinitionEntity>()
                .eq(FlowDefinitionEntity::getChartKey, chartKey)
                .eq(FlowDefinitionEntity::getPublishStatus, status)
                .orderByDesc(FlowDefinitionEntity::getChartVersion)
                .last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    /**
     * "One row per chartKey at its latest version" pagination.
     *
     * <p>Why a raw {@link QueryWrapper} with a sub-select instead of LambdaQueryWrapper?
     * MyBatis-Plus' lambda variant cannot express the {@code (chart_key, MAX(chart_version))}
     * self-join cleanly; switching to a sub-select keeps the query single-statement and lets
     * the database planner do its job (indexes on chart_key + chart_version handle the heavy
     * lifting). The sub-select is parameter-free so it is also safe against injection - all
     * user-supplied {@code keyword/status} only flow through bind variables.
     */
    @Override
    public IPage<FlowDefinitionEntity> pageLatest(IPage<FlowDefinitionEntity> page, String keyword, String status) {
        QueryWrapper<FlowDefinitionEntity> wrapper = new QueryWrapper<>();
        wrapper.inSql("chart_id",
                "SELECT MAX(chart_id) FROM flow_definition GROUP BY chart_key");
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like("chart_name", like).or().like("chart_key", like));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("publish_status", status);
        }
        wrapper.orderByDesc("update_time", "create_time");
        return mapper.selectPage(page, wrapper);
    }
}
