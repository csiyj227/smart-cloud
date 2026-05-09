package com.smart.codegen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.codegen.entity.GenTemplate;
import com.smart.codegen.mapper.GenTemplateMapper;
import com.smart.codegen.service.GenTemplateService;
import org.springframework.stereotype.Service;

@Service
public class GenTemplateServiceImpl extends ServiceImpl<GenTemplateMapper, GenTemplate> implements GenTemplateService {
}