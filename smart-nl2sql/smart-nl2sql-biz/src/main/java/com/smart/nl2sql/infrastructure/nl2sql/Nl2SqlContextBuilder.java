package com.smart.nl2sql.infrastructure.nl2sql;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetColumnEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetRelationEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetSampleEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetTableEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlKnowledgeEntity;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetColumnMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetRelationMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetSampleMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetTableMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlKnowledgeMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Loads dataset metadata, knowledge and samples that will be fed into the NL2SQL prompt.
 */
@Component
@RequiredArgsConstructor
public class Nl2SqlContextBuilder {

    private final Nl2sqlDatasetTableMapper tableMapper;
    private final Nl2sqlDatasetColumnMapper columnMapper;
    private final Nl2sqlDatasetRelationMapper relationMapper;
    private final Nl2sqlDatasetSampleMapper sampleMapper;
    private final Nl2sqlKnowledgeMapper knowledgeMapper;

    public Context build(Long datasetId) {
        Context ctx = new Context();
        ctx.setTables(tableMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetTableEntity>()
                        .eq(Nl2sqlDatasetTableEntity::getDatasetId, datasetId)));
        ctx.setColumns(columnMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetColumnEntity>()
                        .eq(Nl2sqlDatasetColumnEntity::getDatasetId, datasetId)
                        .orderByAsc(Nl2sqlDatasetColumnEntity::getTableName)
                        .orderByAsc(Nl2sqlDatasetColumnEntity::getSortOrder)));
        ctx.setRelations(relationMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetRelationEntity>()
                        .eq(Nl2sqlDatasetRelationEntity::getDatasetId, datasetId)));
        ctx.setKnowledge(knowledgeMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlKnowledgeEntity>()
                        .eq(Nl2sqlKnowledgeEntity::getDatasetId, datasetId)
                        .eq(Nl2sqlKnowledgeEntity::getStatus, 1)));
        ctx.setSamples(sampleMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetSampleEntity>()
                        .eq(Nl2sqlDatasetSampleEntity::getDatasetId, datasetId)
                        .last("limit 5")));
        return ctx;
    }

    @Data
    public static class Context {
        private List<Nl2sqlDatasetTableEntity> tables;
        private List<Nl2sqlDatasetColumnEntity> columns;
        private List<Nl2sqlDatasetRelationEntity> relations;
        private List<Nl2sqlKnowledgeEntity> knowledge;
        private List<Nl2sqlDatasetSampleEntity> samples;
    }
}
