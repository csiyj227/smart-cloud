package com.smart.nl2sql.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据集向量索引段。
 *
 * <p>「数据集学习」会把表/字段/关系/样例/知识切片成多条 segment，对每条 segment 的
 * {@link #content} 调用 EmbeddingClient 生成 1024 维向量写入 {@link #embedding}。
 * 对话时根据用户问题的向量做余弦距离 top-K 检索（由 Mapper 层提供 SQL）。
 *
 * <p>注意：embedding 字段在 Java 侧用 {@code String} 表示「pgvector 字面量」（如
 * "[0.12,0.34,...]"），由 Mapper 的自定义 SQL 强转为 {@code ::vector} 类型。
 * 用 String 的好处是不依赖任何 pgvector 的 JDBC 类型映射 jar，与 smart-ai 完全解耦。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nl2sql_dataset_segment")
public class Nl2sqlDatasetSegmentEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    /** table / column / relation / sample / knowledge */
    private String segmentType;

    /** 引用源对象的主键 id（如 column 段对应 nl2sql_dataset_column.id） */
    private Long refId;

    /** 引用源对象所属的表名（便于按表过滤召回） */
    private String refTable;

    /** 人读标签（如 "table:orders" / "column:orders.amount"） */
    private String refLabel;

    /** 切片化文本（同时用于 embedding 生成 + LLM 上下文回填） */
    private String content;

    /** 估算 token 数；当前用 content.length()/4 粗估，可后续替换为 tokenizer */
    private Integer tokenCount;

    /**
     * 向量字面量字符串，如 "[0.12,0.34,-0.56,...]"。
     * 实际写入数据库时由 Mapper 层用 {@code ?::vector} 强转。
     * 不参与 MyBatis-Plus 的默认 SQL 生成（用 exist=false 防止污染）。
     */
    @TableField(exist = false)
    private String embedding;
}
