package com.smart.codegen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.codegen.entity.GenTable;
import com.smart.codegen.mapper.GenTableMapper;
import com.smart.codegen.service.GenTableService;
import org.springframework.stereotype.Service;

@Service
public class GenTableServiceImpl extends ServiceImpl<GenTableMapper, GenTable> implements GenTableService {
}