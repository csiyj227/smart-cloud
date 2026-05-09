package com.smart.codegen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.codegen.entity.GenTemplateGroup;
import com.smart.codegen.mapper.GenTemplateGroupMapper;
import com.smart.codegen.service.GenTemplateGroupService;
import org.springframework.stereotype.Service;

@Service
public class GenTemplateGroupServiceImpl extends ServiceImpl<GenTemplateGroupMapper, GenTemplateGroup> implements GenTemplateGroupService {
}