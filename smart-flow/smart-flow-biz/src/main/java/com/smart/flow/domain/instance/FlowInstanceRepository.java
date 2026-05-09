package com.smart.flow.domain.instance;

import com.smart.flow.infrastructure.persistence.entity.FlowInstanceBizEntity;

import java.util.Optional;

/**
 * Domain port for {@code flow_instance_biz} access. Mirrors the layering used by
 * {@code FlowDefinitionRepository}: the application layer never touches MyBatis-Plus
 * directly, which makes it trivial to introduce caching or replace the persistence
 * technology later.
 */
public interface FlowInstanceRepository {

    FlowInstanceBizEntity save(FlowInstanceBizEntity entity);

    Optional<FlowInstanceBizEntity> findById(Long bizId);

    Optional<FlowInstanceBizEntity> findByProcessInstanceId(String processInstanceId);
}
