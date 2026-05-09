package com.smart.ai.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.ai.api.dto.ModelConfigCmd;
import com.smart.ai.infrastructure.persistence.entity.AiModelConfigEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Model configuration management service.
 */
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final AiModelConfigMapper configMapper;

    public IPage<AiModelConfigEntity> page(Page<AiModelConfigEntity> page, Long providerId) {
        return configMapper.selectPage(page, Wrappers.<AiModelConfigEntity>lambdaQuery()
                .eq(providerId != null, AiModelConfigEntity::getProviderId, providerId)
                .eq(AiModelConfigEntity::getStatus, "1")
                .orderByDesc(AiModelConfigEntity::getIsDefault));
    }

    public List<AiModelConfigEntity> listByProvider(Long providerId) {
        return configMapper.selectList(Wrappers.<AiModelConfigEntity>lambdaQuery()
                .eq(providerId != null, AiModelConfigEntity::getProviderId, providerId)
                .eq(AiModelConfigEntity::getStatus, "1"));
    }

    public List<AiModelConfigEntity> listAll() {
        return configMapper.selectList(Wrappers.<AiModelConfigEntity>lambdaQuery()
                .eq(AiModelConfigEntity::getStatus, "1")
                .orderByDesc(AiModelConfigEntity::getIsDefault));
    }

    public AiModelConfigEntity getById(Long id) {
        return configMapper.selectById(id);
    }

    public Long save(ModelConfigCmd cmd) {
        AiModelConfigEntity entity = new AiModelConfigEntity();
        BeanUtils.copyProperties(cmd, entity);
        configMapper.insert(entity);
        return entity.getId();
    }

    public void update(ModelConfigCmd cmd) {
        AiModelConfigEntity entity = new AiModelConfigEntity();
        BeanUtils.copyProperties(cmd, entity);
        configMapper.updateById(entity);
    }

    public void delete(Long id) {
        configMapper.deleteById(id);
    }
}
