package com.smart.ai.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.ai.infrastructure.persistence.entity.AiMcpServerEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiMcpServerMapper extends BaseMapper<AiMcpServerEntity> {
}
