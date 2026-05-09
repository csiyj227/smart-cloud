package com.smart.codegen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.codegen.entity.GenTableColumn;
import com.smart.codegen.mapper.GenTableColumnMapper;
import com.smart.codegen.service.GenTableColumnService;
import org.springframework.stereotype.Service;

@Service
public class GenTableColumnServiceImpl extends ServiceImpl<GenTableColumnMapper, GenTableColumn> implements GenTableColumnService {
}