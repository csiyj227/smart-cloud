package com.smart.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * System user type enumeration.
 */
@Getter
@AllArgsConstructor
public enum UserType {

    ADMIN("0", "管理员"),
    NORMAL("1", "普通用户");

    private final String value;
    private final String label;
}