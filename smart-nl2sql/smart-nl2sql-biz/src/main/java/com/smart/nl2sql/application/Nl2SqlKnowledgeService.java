package com.smart.nl2sql.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.nl2sql.api.dto.Nl2SqlKnowledgeDTO;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlKnowledgeEntity;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class Nl2SqlKnowledgeService {

    private final Nl2sqlKnowledgeMapper knowledgeMapper;

    public Page<Nl2sqlKnowledgeEntity> page(Page<Nl2sqlKnowledgeEntity> page, Long datasetId, String type) {
        LambdaQueryWrapper<Nl2sqlKnowledgeEntity> wrapper = new LambdaQueryWrapper<>();
        if (datasetId != null) {
            wrapper.eq(Nl2sqlKnowledgeEntity::getDatasetId, datasetId);
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(Nl2sqlKnowledgeEntity::getType, type);
        }
        wrapper.orderByDesc(Nl2sqlKnowledgeEntity::getCreateTime);
        return knowledgeMapper.selectPage(page, wrapper);
    }

    public Nl2sqlKnowledgeEntity getById(Long id) {
        return knowledgeMapper.selectById(id);
    }

    @Transactional
    public void create(Nl2SqlKnowledgeDTO dto) {
        Nl2sqlKnowledgeEntity entity = new Nl2sqlKnowledgeEntity();
        BeanUtils.copyProperties(dto, entity);
        knowledgeMapper.insert(entity);
    }

    @Transactional
    public void update(Nl2SqlKnowledgeDTO dto) {
        Nl2sqlKnowledgeEntity entity = knowledgeMapper.selectById(dto.getId());
        if (entity == null) {
            throw new IllegalArgumentException("知识条目不存在");
        }
        BeanUtils.copyProperties(dto, entity);
        knowledgeMapper.updateById(entity);
    }

    @Transactional
    public void delete(Long id) {
        knowledgeMapper.deleteById(id);
    }

    public List<Nl2sqlKnowledgeEntity> listByDatasetId(Long datasetId) {
        return knowledgeMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlKnowledgeEntity>()
                        .eq(Nl2sqlKnowledgeEntity::getDatasetId, datasetId));
    }
}
