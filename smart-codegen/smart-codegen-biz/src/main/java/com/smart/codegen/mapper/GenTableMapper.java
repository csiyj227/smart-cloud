package com.smart.codegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.codegen.entity.GenTable;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GenTableMapper extends BaseMapper<GenTable> {
}