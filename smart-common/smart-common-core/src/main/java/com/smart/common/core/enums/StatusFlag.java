package com.smart.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Reusable status flags shared across domain entities.
 *
 * <p>Replaces scattered String constants with type-safe enums.
 */
@Getter
@AllArgsConstructor
public enum StatusFlag {

    ENABLED("0", "正常"),
    DISABLED("1", "停用"),
    DELETED("1", "已删除"),
    NOT_DELETED("0", "未删除"),
    LOCKED("9", "锁定"),
    UNLOCKED("0", "正常"),
    SUCCESS("0", "成功"),
    FAIL("1", "失败");

    private final String value;
    private final String label;
}