package com.smart.flow.domain.definition;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smart.flow.infrastructure.persistence.entity.FlowDefinitionEntity;

import java.util.List;
import java.util.Optional;

/**
 * Domain-facing port for accessing {@code FlowDefinitionEntity}.
 *
 * <p>Defining the port in the {@code domain} layer (rather than letting application code call
 * the MyBatis-Plus mapper directly) keeps the application services free of persistence
 * concerns and makes the next-version cache layer / read-replica work straightforward.
 */
public interface FlowDefinitionRepository {

    FlowDefinitionEntity save(FlowDefinitionEntity entity);

    Optional<FlowDefinitionEntity> findById(Long chartId);

    Optional<FlowDefinitionEntity> findByKeyAndVersion(String chartKey, Integer version);

    Optional<FlowDefinitionEntity> findLatestPublished(String chartKey);

    Optional<FlowDefinitionEntity> findLatestDraft(String chartKey);

    List<FlowDefinitionEntity> listByChartKey(String chartKey);

    Integer nextVersion(String chartKey);

    /**
     * Pagination for the front-end "flow definition list" page.
     *
     * <p>Returns one row per (chartKey, latest-version) tuple - we deliberately do not list
     * every version because the list page is the entry into the designer, where users care
     * about "which charts exist" not "every historical revision". Use {@link #listByChartKey}
     * to walk through versions of a single chart instead.
     *
     * @param page     MyBatis-Plus page descriptor (current/size)
     * @param keyword  optional fuzzy match against chartName / chartKey, {@code null} = no filter
     * @param status   optional exact match against publish_status, {@code null} = no filter
     */
    IPage<FlowDefinitionEntity> pageLatest(IPage<FlowDefinitionEntity> page, String keyword, String status);
}
