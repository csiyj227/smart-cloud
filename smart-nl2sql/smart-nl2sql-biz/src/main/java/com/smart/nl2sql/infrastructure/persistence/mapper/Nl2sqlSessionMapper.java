package com.smart.nl2sql.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlSessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface Nl2sqlSessionMapper extends BaseMapper<Nl2sqlSessionEntity> {
}