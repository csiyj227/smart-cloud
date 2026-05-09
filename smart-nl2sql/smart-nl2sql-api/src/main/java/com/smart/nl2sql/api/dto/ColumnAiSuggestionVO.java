package com.smart.nl2sql.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 对单个字段的元数据建议结果。
 *
 * <p>仅作为「建议」返回给前端展示，不直接落库；用户在 UI 上确认采纳后，
 * 由前端通过 {@code PUT /nl2sql/dataset/{id}/columns} 持久化。
 */
@Data
public class ColumnAiSuggestionVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 对应 nl2sql_dataset_column.id */
    private Long columnId;
    private String tableName;
    private String columnName;
    private String columnType;

    /** 当前已存在的用户备注（前端用以判断是否高亮"已维护"） */
    private String currentUserRemark;

    /** AI 建议的备注文本（业务含义/单位/枚举值等） */
    private String suggestedRemark;

    /** AI 建议的维度标记 */
    private Boolean suggestedIsDimension;

    /** AI 建议的度量标记 */
    private Boolean suggestedIsMeasure;
}
