package com.smart.nl2sql.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LearnStatus {
    NOT_LEARNED(0, "未学习"),
    LEARNING(1, "学习中"),
    LEARNED(2, "已学习"),
    LEARN_FAILED(3, "学习失败");

    private final int code;
    private final String label;
}