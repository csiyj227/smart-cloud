package com.smart.ai.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Message processing status.
 */
@Getter
@RequiredArgsConstructor
public enum MessageStatusEnum {

    SENDING("SENDING", "Being sent"),
    STREAMING("STREAMING", "Streaming response"),
    SUCCESS("SUCCESS", "Completed"),
    ERROR("ERROR", "Failed");

    private final String code;
    private final String description;
}
