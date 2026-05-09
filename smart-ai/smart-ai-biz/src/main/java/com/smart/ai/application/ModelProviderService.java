package com.smart.ai.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.ai.api.dto.ModelProviderCmd;
import com.smart.ai.infrastructure.persistence.entity.AiModelProviderEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiModelProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Model provider management service.
 */
@Service
@RequiredArgsConstructor
public class ModelProviderService {

    private final AiModelProviderMapper providerMapper;

    public IPage<AiModelProviderEntity> page(Page<AiModelProviderEntity> page, String keyword) {
        return providerMapper.selectPage(page, Wrappers.<AiModelProviderEntity>lambdaQuery()
                .like(keyword != null, AiModelProviderEntity::getProviderName, keyword)
                .orderByAsc(AiModelProviderEntity::getSortOrder));
    }

    public List<AiModelProviderEntity> list() {
        return providerMapper.selectList(Wrappers.<AiModelProviderEntity>lambdaQuery()
                .eq(AiModelProviderEntity::getStatus, "1")
                .orderByAsc(AiModelProviderEntity::getSortOrder));
    }

    public AiModelProviderEntity getById(Long id) {
        return providerMapper.selectById(id);
    }

    public Long save(ModelProviderCmd cmd) {
        AiModelProviderEntity entity = new AiModelProviderEntity();
        BeanUtils.copyProperties(cmd, entity);
        providerMapper.insert(entity);
        return entity.getId();
    }

    public void update(ModelProviderCmd cmd) {
        AiModelProviderEntity entity = new AiModelProviderEntity();
        BeanUtils.copyProperties(cmd, entity);
        providerMapper.updateById(entity);
    }

    public void delete(Long id) {
        providerMapper.deleteById(id);
    }
}
