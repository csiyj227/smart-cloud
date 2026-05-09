package com.smart.nl2sql.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.nl2sql.infrastructure.llm.EmbeddingClient;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetColumnEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetRelationEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetSampleEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetSegmentEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetTableEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlKnowledgeEntity;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetColumnMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetRelationMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetSampleMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetSegmentMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetTableMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集向量索引器：把数据集的元数据 / 关系 / 样例 / 知识切片成可检索的文本段，
 * 调用 {@link EmbeddingClient} 生成向量后写入 {@code nl2sql_dataset_segment} 表。
 *
 * <p>切片策略：
 * <ul>
 *     <li><b>table 段</b>：每张表一段，包含表名/别名/注释/主键字段列表，用于"这个问题涉及哪些表"召回</li>
 *     <li><b>column 段</b>：每个字段一段，包含 表名.字段名 + 类型 + 原注释 + user_remark + dim/measure 标记</li>
 *     <li><b>relation 段</b>：每条 JOIN 关系一段</li>
 *     <li><b>sample 段</b>：每条 Few-Shot 样例一段（question + sql_text）</li>
 *     <li><b>knowledge 段</b>：每条业务知识一段（按 type 分别说明）</li>
 * </ul>
 *
 * <p>学习是「全量重建」语义：先 delete 该 dataset 的所有段，再批量插入。
 * 保证 idempotent，重复学习不会产生重复段；同时避免渐进更新带来的脏数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetVectorIndexer {

    /** 单批送 embedding 的最大条数；过大会触发模型 API 限流 / 超时 */
    private static final int EMBED_BATCH_SIZE = 16;

    private final Nl2sqlDatasetMapper datasetMapper;
    private final Nl2sqlDatasetTableMapper tableMapper;
    private final Nl2sqlDatasetColumnMapper columnMapper;
    private final Nl2sqlDatasetRelationMapper relationMapper;
    private final Nl2sqlDatasetSampleMapper sampleMapper;
    private final Nl2sqlKnowledgeMapper knowledgeMapper;
    private final Nl2sqlDatasetSegmentMapper segmentMapper;
    private final EmbeddingClient embeddingClient;

    /**
     * 全量重建数据集的向量索引。
     *
     * @param datasetId 数据集 id
     * @return 实际写入的段数（embedding 失败时已自动跳过）
     */
    @Transactional
    public int rebuild(Long datasetId) {
        Nl2sqlDatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("数据集不存在: " + datasetId);
        }

        // 1. 清空旧段（learn 是幂等操作）
        int deleted = segmentMapper.deleteByDatasetId(datasetId);
        if (deleted > 0) {
            log.info("[VectorIndex] dataset={} 清空旧向量段 {} 条", datasetId, deleted);
        }

        // 2. 切片
        List<Nl2sqlDatasetSegmentEntity> segments = buildSegments(datasetId);
        if (segments.isEmpty()) {
            log.warn("[VectorIndex] dataset={} 没有可索引的内容（表/字段都为空？）", datasetId);
            return 0;
        }

        // 3. 批量 embedding（按 EMBED_BATCH_SIZE 切批，防限流）
        int written = 0;
        for (int from = 0; from < segments.size(); from += EMBED_BATCH_SIZE) {
            int to = Math.min(from + EMBED_BATCH_SIZE, segments.size());
            List<Nl2sqlDatasetSegmentEntity> batch = segments.subList(from, to);
            List<String> texts = batch.stream().map(Nl2sqlDatasetSegmentEntity::getContent).toList();
            List<float[]> vectors;
            try {
                vectors = embeddingClient.embedBatch(texts);
            } catch (RuntimeException ex) {
                // 单批失败不阻断整体，跳过这一批继续；调用方拿到 written < segments.size() 也可识别
                log.error("[VectorIndex] dataset={} 批次 [{},{}) embedding 失败，已跳过: {}",
                        datasetId, from, to, ex.getMessage());
                continue;
            }
            if (vectors.size() != batch.size()) {
                log.error("[VectorIndex] dataset={} 批次 [{},{}) embedding 返回数量异常: 期望 {}, 实际 {}",
                        datasetId, from, to, batch.size(), vectors.size());
                continue;
            }
            for (int i = 0; i < batch.size(); i++) {
                Nl2sqlDatasetSegmentEntity seg = batch.get(i);
                seg.setEmbedding(EmbeddingClient.toVectorLiteral(vectors.get(i)));
                segmentMapper.insertWithVector(seg);
                written++;
            }
        }
        log.info("[VectorIndex] dataset={} 学习完成：候选段 {} / 实际写入 {}",
                datasetId, segments.size(), written);
        return written;
    }

    // -----------------------------------------------------------------------
    // 切片构造（按段类型分别构造，保持顺序：table → column → relation → sample → knowledge）
    // -----------------------------------------------------------------------

    private List<Nl2sqlDatasetSegmentEntity> buildSegments(Long datasetId) {
        List<Nl2sqlDatasetTableEntity> tables = tableMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetTableEntity>()
                        .eq(Nl2sqlDatasetTableEntity::getDatasetId, datasetId));
        List<Nl2sqlDatasetColumnEntity> columns = columnMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetColumnEntity>()
                        .eq(Nl2sqlDatasetColumnEntity::getDatasetId, datasetId)
                        .orderByAsc(Nl2sqlDatasetColumnEntity::getTableName)
                        .orderByAsc(Nl2sqlDatasetColumnEntity::getSortOrder));
        List<Nl2sqlDatasetRelationEntity> relations = relationMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetRelationEntity>()
                        .eq(Nl2sqlDatasetRelationEntity::getDatasetId, datasetId));
        List<Nl2sqlDatasetSampleEntity> samples = sampleMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetSampleEntity>()
                        .eq(Nl2sqlDatasetSampleEntity::getDatasetId, datasetId));
        List<Nl2sqlKnowledgeEntity> knowledges = knowledgeMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlKnowledgeEntity>()
                        .eq(Nl2sqlKnowledgeEntity::getDatasetId, datasetId));

        // 字段按表分组，构造 table 段时把主键列表带进去；构造 column 段时单独切片
        Map<String, List<Nl2sqlDatasetColumnEntity>> columnsByTable = new LinkedHashMap<>();
        for (Nl2sqlDatasetColumnEntity c : columns) {
            columnsByTable.computeIfAbsent(c.getTableName(), k -> new ArrayList<>()).add(c);
        }

        List<Nl2sqlDatasetSegmentEntity> result = new ArrayList<>(
                tables.size() + columns.size() + relations.size() + samples.size() + knowledges.size());

        // === table 段 ===
        for (Nl2sqlDatasetTableEntity t : tables) {
            List<Nl2sqlDatasetColumnEntity> cols = columnsByTable.getOrDefault(t.getTableName(), List.of());
            String pkList = cols.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsPrimaryKey()))
                    .map(Nl2sqlDatasetColumnEntity::getColumnName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(无)");
            StringBuilder sb = new StringBuilder();
            sb.append("表：").append(t.getTableName());
            if (notBlank(t.getTableAlias())) {
                sb.append(" (业务别名：").append(t.getTableAlias()).append(")");
            }
            sb.append("\n");
            if (notBlank(t.getTableComment())) {
                sb.append("说明：").append(t.getTableComment()).append("\n");
            }
            sb.append("主键：").append(pkList).append("\n");
            sb.append("字段数：").append(cols.size());
            result.add(buildSegment(datasetId, "table", t.getId(), t.getTableName(),
                    "table:" + t.getTableName(), sb.toString()));
        }

        // === column 段 ===
        for (Nl2sqlDatasetColumnEntity c : columns) {
            StringBuilder sb = new StringBuilder();
            sb.append("字段：").append(c.getTableName()).append('.').append(c.getColumnName())
                    .append(" (").append(c.getColumnType()).append(")\n");
            if (notBlank(c.getColumnComment())) {
                sb.append("原注释：").append(c.getColumnComment()).append("\n");
            }
            if (notBlank(c.getUserRemark())) {
                sb.append("业务备注：").append(c.getUserRemark()).append("\n");
            }
            List<String> tags = new ArrayList<>(3);
            if (Boolean.TRUE.equals(c.getIsPrimaryKey())) tags.add("主键");
            if (Boolean.TRUE.equals(c.getIsDimension())) tags.add("维度");
            if (Boolean.TRUE.equals(c.getIsMeasure())) tags.add("度量");
            if (!tags.isEmpty()) {
                sb.append("标记：").append(String.join("/", tags));
            }
            result.add(buildSegment(datasetId, "column", c.getId(), c.getTableName(),
                    "column:" + c.getTableName() + "." + c.getColumnName(), sb.toString()));
        }

        // === relation 段 ===
        for (Nl2sqlDatasetRelationEntity r : relations) {
            String content = String.format("表关系：%s.%s %s %s.%s",
                    r.getSourceTable(), r.getSourceColumn(),
                    r.getRelationType() != null ? r.getRelationType() : "JOIN",
                    r.getTargetTable(), r.getTargetColumn());
            result.add(buildSegment(datasetId, "relation", r.getId(), r.getSourceTable(),
                    "relation:" + r.getSourceTable() + "->" + r.getTargetTable(), content));
        }

        // === sample 段 ===
        for (Nl2sqlDatasetSampleEntity s : samples) {
            StringBuilder sb = new StringBuilder();
            sb.append("问题示例：").append(s.getQuestion()).append("\n");
            sb.append("SQL：").append(s.getSqlText());
            if (notBlank(s.getExplanation())) {
                sb.append("\n说明：").append(s.getExplanation());
            }
            result.add(buildSegment(datasetId, "sample", s.getId(), null,
                    "sample:" + truncate(s.getQuestion(), 40), sb.toString()));
        }

        // === knowledge 段 ===
        for (Nl2sqlKnowledgeEntity k : knowledges) {
            StringBuilder sb = new StringBuilder();
            sb.append("[知识/").append(k.getType()).append("] ");
            if (notBlank(k.getTitle())) {
                sb.append(k.getTitle()).append("\n");
            }
            sb.append(k.getContent());
            result.add(buildSegment(datasetId, "knowledge", k.getId(), null,
                    "knowledge:" + (notBlank(k.getTitle()) ? k.getTitle() : k.getType()),
                    sb.toString()));
        }

        return result;
    }

    private static Nl2sqlDatasetSegmentEntity buildSegment(Long datasetId, String type, Long refId,
                                                           String refTable, String label, String content) {
        Nl2sqlDatasetSegmentEntity e = new Nl2sqlDatasetSegmentEntity();
        e.setDatasetId(datasetId);
        e.setSegmentType(type);
        e.setRefId(refId);
        e.setRefTable(refTable);
        e.setRefLabel(label);
        e.setContent(content);
        // 粗略 token 估算：英文 ~4 字符/token，中文 ~1.5 字符/token，取 3 折中
        e.setTokenCount(content.length() / 3);
        return e;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
