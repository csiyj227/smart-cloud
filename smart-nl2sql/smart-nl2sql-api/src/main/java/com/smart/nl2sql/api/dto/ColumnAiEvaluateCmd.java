package com.smart.nl2sql.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 字段 AI 评估命令。
 *
 * <p>前端可以选择性指定要评估的表或字段子集；都为空时评估整个数据集中
 * 「user_remark 为空」的字段（避免覆盖用户已维护的内容）。
 */
@Data
public class ColumnAiEvaluateCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 限定只评估指定表的字段；为空表示所有表 */
    private List<String> tableNames;

    /** 限定只评估指定字段 id；为空表示所有字段（仍受 onlyEmptyRemark 影响） */
    private List<Long> columnIds;

    /**
     * 是否只评估「用户备注为空」的字段。默认 true（不覆盖用户手填内容）。
     * 单条采纳时前端可以传 false 强制重新评估某一行。
     */
    private Boolean onlyEmptyRemark = true;
}
