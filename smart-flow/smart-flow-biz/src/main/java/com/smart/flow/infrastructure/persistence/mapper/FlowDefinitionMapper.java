package com.smart.flow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.flow.infrastructure.persistence.entity.FlowDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for {@code flow_definition}. No custom SQL is needed at this stage -
 * the BaseMapper covers CRUD and the application service uses LambdaQueryWrapper for the
 * version-and-status queries.
 */
@Mapper
public interface FlowDefinitionMapper extends BaseMapper<FlowDefinitionEntity> {
}
