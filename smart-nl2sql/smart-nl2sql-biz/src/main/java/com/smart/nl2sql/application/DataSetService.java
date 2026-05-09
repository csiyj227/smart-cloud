package com.smart.nl2sql.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.nl2sql.api.dto.*;
import com.smart.nl2sql.api.enums.LearnStatus;
import com.smart.nl2sql.infrastructure.persistence.entity.*;
import com.smart.nl2sql.infrastructure.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSetService {

    private final Nl2sqlDatasetMapper datasetMapper;
    private final Nl2sqlDatasetTableMapper tableMapper;
    private final Nl2sqlDatasetColumnMapper columnMapper;
    private final Nl2sqlDatasetRelationMapper relationMapper;
    private final Nl2sqlDatasetSampleMapper sampleMapper;
    private final DataSourceService dataSourceService;

    /**
     * 学习流程依赖的 AI 评估器与向量索引器。
     * 不用构造注入是为了：
     *   1) 避免和这两个 Bean 形成构造期相互引用（虽然当前没有，但为后续扩展留出余地）；
     *   2) 它们内部依赖 LlmClient/EmbeddingClient/SpringAI，启动顺序滞后，@Lazy 确保不会卡住启动；
     *   3) setter 注入便于单元测试替换 mock。
     */
    private ColumnAiEvaluator columnAiEvaluator;
    private DatasetVectorIndexer datasetVectorIndexer;

    @Autowired
    public void setColumnAiEvaluator(@Lazy ColumnAiEvaluator columnAiEvaluator) {
        this.columnAiEvaluator = columnAiEvaluator;
    }

    @Autowired
    public void setDatasetVectorIndexer(@Lazy DatasetVectorIndexer datasetVectorIndexer) {
        this.datasetVectorIndexer = datasetVectorIndexer;
    }

    /**
     * 数值类型集合（小写匹配，前缀即可）。这些类型默认作为「度量」候选；
     * 其它类型默认作为「维度」候选。主键字段无论类型如何都不应作为度量。
     *
     * <p>覆盖 MySQL / PostgreSQL / Oracle / GaussDB 主流数值类型。
     */
    private static final Set<String> NUMERIC_TYPE_PREFIXES = Set.of(
            "int", "integer", "bigint", "smallint", "tinyint", "mediumint",
            "decimal", "numeric", "number",
            "float", "double", "real", "money", "smallmoney",
            "serial", "bigserial", "smallserial");

    /** 按 column_type 推断字段是否为度量（数值且非主键）。 */
    static boolean inferIsMeasure(String columnType, boolean isPrimaryKey) {
        if (isPrimaryKey || columnType == null) {
            return false;
        }
        String t = columnType.trim().toLowerCase();
        // 类型可能是 "int8"、"varchar(255)"、"numeric(10,2)"，按前缀匹配
        for (String prefix : NUMERIC_TYPE_PREFIXES) {
            if (t.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 度量的"反面"基本就是维度，但主键也不视作维度（主键单独标）。 */
    static boolean inferIsDimension(String columnType, boolean isPrimaryKey) {
        if (isPrimaryKey) {
            return false;
        }
        return !inferIsMeasure(columnType, false);
    }

    public Page<Nl2sqlDatasetEntity> page(Page<Nl2sqlDatasetEntity> page, String keyword) {
        LambdaQueryWrapper<Nl2sqlDatasetEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Nl2sqlDatasetEntity::getName, keyword);
        }
        wrapper.orderByDesc(Nl2sqlDatasetEntity::getCreateTime);
        return datasetMapper.selectPage(page, wrapper);
    }

    public DataSetDTO getDetail(Long id) {
        Nl2sqlDatasetEntity entity = datasetMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("数据集不存在");
        }
        DataSetDTO dto = new DataSetDTO();
        BeanUtils.copyProperties(entity, dto);

        List<Nl2sqlDatasetTableEntity> tables = tableMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetTableEntity>()
                        .eq(Nl2sqlDatasetTableEntity::getDatasetId, id));

        List<Nl2sqlDatasetColumnEntity> allColumns = columnMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetColumnEntity>()
                        .eq(Nl2sqlDatasetColumnEntity::getDatasetId, id)
                        .orderByAsc(Nl2sqlDatasetColumnEntity::getSortOrder));

        List<DataSetTableDTO> tableDTOs = tables.stream().map(table -> {
            DataSetTableDTO tableDTO = new DataSetTableDTO();
            BeanUtils.copyProperties(table, tableDTO);
            List<DataSetColumnDTO> columnDTOs = allColumns.stream()
                    .filter(col -> col.getTableName().equals(table.getTableName()))
                    .map(col -> {
                        DataSetColumnDTO colDTO = new DataSetColumnDTO();
                        BeanUtils.copyProperties(col, colDTO);
                        return colDTO;
                    })
                    .collect(Collectors.toList());
            tableDTO.setColumns(columnDTOs);
            return tableDTO;
        }).collect(Collectors.toList());
        dto.setTables(tableDTOs);

        List<Nl2sqlDatasetRelationEntity> relations = relationMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetRelationEntity>()
                        .eq(Nl2sqlDatasetRelationEntity::getDatasetId, id));
        List<DataSetRelationDTO> relationDTOs = relations.stream().map(rel -> {
            DataSetRelationDTO relDTO = new DataSetRelationDTO();
            BeanUtils.copyProperties(rel, relDTO);
            return relDTO;
        }).collect(Collectors.toList());
        dto.setRelations(relationDTOs);

        return dto;
    }

    @Transactional
    public Long create(DataSetDTO dto) {
        Nl2sqlDatasetEntity entity = new Nl2sqlDatasetEntity();
        entity.setName(dto.getName());
        entity.setDatasourceId(dto.getDatasourceId());
        entity.setDescription(dto.getDescription());
        entity.setLearnStatus(0);
        entity.setStatus(1);
        datasetMapper.insert(entity);
        return entity.getId();
    }

    @Transactional
    public void update(DataSetDTO dto) {
        Nl2sqlDatasetEntity entity = datasetMapper.selectById(dto.getId());
        if (entity == null) {
            throw new IllegalArgumentException("数据集不存在");
        }
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        datasetMapper.updateById(entity);
    }

    @Transactional
    public void delete(Long id) {
        datasetMapper.deleteById(id);
        tableMapper.delete(new LambdaQueryWrapper<Nl2sqlDatasetTableEntity>()
                .eq(Nl2sqlDatasetTableEntity::getDatasetId, id));
        columnMapper.delete(new LambdaQueryWrapper<Nl2sqlDatasetColumnEntity>()
                .eq(Nl2sqlDatasetColumnEntity::getDatasetId, id));
        relationMapper.delete(new LambdaQueryWrapper<Nl2sqlDatasetRelationEntity>()
                .eq(Nl2sqlDatasetRelationEntity::getDatasetId, id));
        sampleMapper.delete(new LambdaQueryWrapper<Nl2sqlDatasetSampleEntity>()
                .eq(Nl2sqlDatasetSampleEntity::getDatasetId, id));
    }

    @Transactional
    public void syncTables(Long datasetId) {
        Nl2sqlDatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("数据集不存在");
        }
        List<Nl2sqlDatasetTableEntity> existingTables = tableMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetTableEntity>()
                        .eq(Nl2sqlDatasetTableEntity::getDatasetId, datasetId));

        for (Nl2sqlDatasetTableEntity table : existingTables) {
            TableMetaVO meta = dataSourceService.getTableColumns(dataset.getDatasourceId(), table.getTableName());
            if (meta == null) {
                continue;
            }
            columnMapper.delete(new LambdaQueryWrapper<Nl2sqlDatasetColumnEntity>()
                    .eq(Nl2sqlDatasetColumnEntity::getDatasetId, datasetId)
                    .eq(Nl2sqlDatasetColumnEntity::getTableName, table.getTableName()));

            int sortOrder = 0;
            for (ColumnMetaVO col : meta.getColumns()) {
                Nl2sqlDatasetColumnEntity colEntity = new Nl2sqlDatasetColumnEntity();
                colEntity.setDatasetId(datasetId);
                colEntity.setTableName(table.getTableName());
                colEntity.setColumnName(col.getColumnName());
                colEntity.setColumnType(col.getColumnType());
                colEntity.setColumnComment(col.getColumnComment());
                boolean isPk = Boolean.TRUE.equals(col.getIsPrimaryKey());
                colEntity.setIsPrimaryKey(isPk);
                // 默认按字段类型推断维度/度量，用户可在前端再调整
                colEntity.setIsDimension(inferIsDimension(col.getColumnType(), isPk));
                colEntity.setIsMeasure(inferIsMeasure(col.getColumnType(), isPk));
                colEntity.setSortOrder(sortOrder++);
                columnMapper.insert(colEntity);
            }
        }
    }

    @Transactional
    public void updateTables(Long datasetId, List<DataSetTableDTO> tables) {
        tableMapper.delete(new LambdaQueryWrapper<Nl2sqlDatasetTableEntity>()
                .eq(Nl2sqlDatasetTableEntity::getDatasetId, datasetId));
        columnMapper.delete(new LambdaQueryWrapper<Nl2sqlDatasetColumnEntity>()
                .eq(Nl2sqlDatasetColumnEntity::getDatasetId, datasetId));

        Nl2sqlDatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("数据集不存在");
        }

        for (DataSetTableDTO tableDTO : tables) {
            Nl2sqlDatasetTableEntity tableEntity = new Nl2sqlDatasetTableEntity();
            tableEntity.setDatasetId(datasetId);
            tableEntity.setTableName(tableDTO.getTableName());
            tableEntity.setTableComment(tableDTO.getTableComment());
            tableEntity.setTableAlias(tableDTO.getTableAlias());
            tableMapper.insert(tableEntity);

            TableMetaVO meta = dataSourceService.getTableColumns(dataset.getDatasourceId(), tableDTO.getTableName());
            if (meta != null) {
                int sortOrder = 0;
                for (ColumnMetaVO col : meta.getColumns()) {
                    Nl2sqlDatasetColumnEntity colEntity = new Nl2sqlDatasetColumnEntity();
                    colEntity.setDatasetId(datasetId);
                    colEntity.setTableName(tableDTO.getTableName());
                    colEntity.setColumnName(col.getColumnName());
                    colEntity.setColumnType(col.getColumnType());
                    colEntity.setColumnComment(col.getColumnComment());
                    boolean isPk = Boolean.TRUE.equals(col.getIsPrimaryKey());
                    colEntity.setIsPrimaryKey(isPk);
                    colEntity.setIsDimension(inferIsDimension(col.getColumnType(), isPk));
                    colEntity.setIsMeasure(inferIsMeasure(col.getColumnType(), isPk));
                    colEntity.setSortOrder(sortOrder++);
                    columnMapper.insert(colEntity);
                }
            }
        }
    }

    @Transactional
    public void updateColumns(Long datasetId, List<DataSetColumnDTO> columns) {
        for (DataSetColumnDTO col : columns) {
            Nl2sqlDatasetColumnEntity entity = columnMapper.selectById(col.getId());
            if (entity != null && entity.getDatasetId().equals(datasetId)) {
                entity.setUserRemark(col.getUserRemark());
                entity.setIsDimension(col.getIsDimension());
                entity.setIsMeasure(col.getIsMeasure());
                entity.setSortOrder(col.getSortOrder());
                columnMapper.updateById(entity);
            }
        }
    }

    /**
     * 触发数据集「学习」流程。当前阶段（向量库尚未接入）做的轻量学习：
     * <ol>
     *     <li>校验完备性：必须有表、有字段</li>
     *     <li>状态置 LEARNING（学习中），方便前端展示进度</li>
     *     <li>autoFillRemark=true 时，调 LLM 给所有「user_remark 为空」的字段补一个备注，
     *         直接落库（这是与 ai-evaluate 接口的关键区别——后者只返建议不落库）</li>
     *     <li>状态置 LEARNED（已学习）+ learnTime；如果中途异常置 LEARN_FAILED</li>
     * </ol>
     *
     * <p>后续接入向量库时，在第 3 步后追加：把表/字段 schema、user_remark、表关系、
     * 样例 SQL 切片向量化入 pgvector 即可，对外接口不变。
     *
     * @param datasetId      数据集 id
     * @param autoFillRemark 是否触发 AI 自动补全空备注（推荐 true，能显著提升 NL2SQL 准确率）
     */
    @Transactional
    public void learn(Long datasetId, boolean autoFillRemark) {
        Nl2sqlDatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("数据集不存在: " + datasetId);
        }

        // 1. 完备性校验
        long tableCount = tableMapper.selectCount(
                new LambdaQueryWrapper<Nl2sqlDatasetTableEntity>()
                        .eq(Nl2sqlDatasetTableEntity::getDatasetId, datasetId));
        if (tableCount == 0) {
            throw new IllegalStateException("数据集尚未选择任何表，请先在「表」标签页添加表");
        }
        long columnCount = columnMapper.selectCount(
                new LambdaQueryWrapper<Nl2sqlDatasetColumnEntity>()
                        .eq(Nl2sqlDatasetColumnEntity::getDatasetId, datasetId));
        if (columnCount == 0) {
            throw new IllegalStateException("字段元数据为空，请先点击「同步表结构」");
        }

        // 2. 标记为学习中（独立小事务，前端轮询能立即看到）
        dataset.setLearnStatus(LearnStatus.LEARNING.getCode());
        datasetMapper.updateById(dataset);

        try {
            // 3. AI 补全空备注 → 落库（注意：与 ai-evaluate 接口不同，这里直接 update）
            if (autoFillRemark && columnAiEvaluator != null) {
                ColumnAiEvaluateCmd cmd = new ColumnAiEvaluateCmd();
                cmd.setOnlyEmptyRemark(true); // 只补空字段，绝不覆盖用户已维护内容
                List<ColumnAiSuggestionVO> suggestions = columnAiEvaluator.evaluate(datasetId, cmd);
                int filled = 0;
                for (ColumnAiSuggestionVO s : suggestions) {
                    if (s.getColumnId() == null || s.getSuggestedRemark() == null
                            || s.getSuggestedRemark().isBlank()) {
                        continue;
                    }
                    Nl2sqlDatasetColumnEntity col = columnMapper.selectById(s.getColumnId());
                    if (col == null || !datasetId.equals(col.getDatasetId())) {
                        continue;
                    }
                    col.setUserRemark(s.getSuggestedRemark());
                    if (Boolean.TRUE.equals(s.getSuggestedIsDimension())) {
                        col.setIsDimension(true);
                    }
                    if (Boolean.TRUE.equals(s.getSuggestedIsMeasure())) {
                        col.setIsMeasure(true);
                    }
                    columnMapper.updateById(col);
                    filled++;
                }
                log.info("[DataSet#learn] datasetId={}, AI 自动补全 {} 个字段备注", datasetId, filled);
            }

            // 4. 全量重建向量索引：把表/字段/关系/样例/知识切片向量化写入 nl2sql_dataset_segment
            //    用 try/catch 包裹是因为：embedding 失败不应让整个学习流程回滚——
            //    用户已维护的字段备注、AI 自动补全的备注都是有价值的中间产物；
            //    向量段缺失时 NL2SQL 对话会降级到「不带 RAG 上下文」的纯 prompt 模式，仍然可用。
            if (datasetVectorIndexer != null) {
                try {
                    int written = datasetVectorIndexer.rebuild(datasetId);
                    log.info("[DataSet#learn] datasetId={} 向量索引完成，写入 {} 条段", datasetId, written);
                } catch (RuntimeException ex) {
                    log.error("[DataSet#learn] datasetId={} 向量索引失败（学习流程其它步骤已完成，对话将降级运行）",
                            datasetId, ex);
                }
            }

            // 5. 标记为已学习
            dataset.setLearnStatus(LearnStatus.LEARNED.getCode());
            dataset.setLearnTime(LocalDateTime.now());
            datasetMapper.updateById(dataset);
            log.info("[DataSet#learn] datasetId={} 学习完成", datasetId);
        } catch (RuntimeException ex) {
            log.error("[DataSet#learn] datasetId={} 学习失败", datasetId, ex);
            dataset.setLearnStatus(LearnStatus.LEARN_FAILED.getCode());
            datasetMapper.updateById(dataset);
            throw ex;
        }
    }

    @Transactional
    public void updateRelations(Long datasetId, List<DataSetRelationDTO> relations) {
        relationMapper.delete(new LambdaQueryWrapper<Nl2sqlDatasetRelationEntity>()
                .eq(Nl2sqlDatasetRelationEntity::getDatasetId, datasetId));
        for (DataSetRelationDTO rel : relations) {
            Nl2sqlDatasetRelationEntity entity = new Nl2sqlDatasetRelationEntity();
            entity.setDatasetId(datasetId);
            entity.setSourceTable(rel.getSourceTable());
            entity.setSourceColumn(rel.getSourceColumn());
            entity.setTargetTable(rel.getTargetTable());
            entity.setTargetColumn(rel.getTargetColumn());
            entity.setRelationType(rel.getRelationType() != null ? rel.getRelationType() : "LEFT JOIN");
            relationMapper.insert(entity);
        }
    }
}
