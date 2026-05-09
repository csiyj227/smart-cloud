package com.smart.common.xss.serializer;

/**
 * Predefined masking strategies for sensitive data desensitization.
 *
 * 预定义的敏感数据脱敏策略。
 */
public enum MaskStrategy {

    DEFAULT,
    NAME,
    PHONE,
    ID_CARD,
    EMAIL,
    BANK_CARD,
    ADDRESS
}