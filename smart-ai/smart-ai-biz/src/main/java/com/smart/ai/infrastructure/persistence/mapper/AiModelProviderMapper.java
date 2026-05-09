package com.smart.ai.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.ai.infrastructure.persistence.entity.AiModelProviderEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelProviderMapper extends BaseMapper<AiModelProviderEntity> {
}
