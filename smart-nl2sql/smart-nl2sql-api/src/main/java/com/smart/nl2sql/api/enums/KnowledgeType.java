package com.smart.nl2sql.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum KnowledgeType {
    SQL_EXAMPLE("sql_example", "SQL示例"),
    TERM("term", "业务术语"),
    RULE("rule", "分析规则"),
    MAPPING("mapping", "维度映射");

    private final String code;
    private final String label;
}