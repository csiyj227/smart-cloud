package com.smart.nl2sql.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChartType {
    TABLE("table", "表格"),
    BAR("bar", "柱状图"),
    LINE("line", "折线图"),
    PIE("pie", "饼图");

    private final String code;
    private final String label;
}