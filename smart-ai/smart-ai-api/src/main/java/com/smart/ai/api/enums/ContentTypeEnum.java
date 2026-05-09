package com.smart.ai.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Message content types.
 */
@Getter
@RequiredArgsConstructor
public enum ContentTypeEnum {

    TEXT("TEXT", "Plain text"),
    IMAGE("IMAGE", "Image"),
    FILE("FILE", "File attachment"),
    MIXED("MIXED", "Mixed content");

    private final String code;
    private final String description;
}
