package com.smart.ai.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Knowledge document parse status.
 */
@Getter
@RequiredArgsConstructor
public enum ParseStatusEnum {

    PENDING("PENDING", "Waiting to parse"),
    PARSING("PARSING", "Parsing in progress"),
    COMPLETED("COMPLETED", "Parse completed"),
    FAILED("FAILED", "Parse failed");

    private final String code;
    private final String description;
}
