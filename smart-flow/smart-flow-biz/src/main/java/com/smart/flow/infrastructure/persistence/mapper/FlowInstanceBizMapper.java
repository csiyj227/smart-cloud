package com.smart.flow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.flow.infrastructure.persistence.entity.FlowInstanceBizEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlowInstanceBizMapper extends BaseMapper<FlowInstanceBizEntity> {
}
