package com.smart.ai.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Chat message roles.
 */
@Getter
@RequiredArgsConstructor
public enum MessageRoleEnum {

    USER("USER", "User message"),
    ASSISTANT("ASSISTANT", "AI assistant reply"),
    SYSTEM("SYSTEM", "System prompt"),
    TOOL("TOOL", "Tool call result");

    private final String code;
    private final String description;
}
