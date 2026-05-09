package com.smart.ai.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * MCP server transport types.
 */
@Getter
@RequiredArgsConstructor
public enum TransportTypeEnum {

    SSE("SSE", "Server-Sent Events"),
    STDIO("STDIO", "Standard I/O");

    private final String code;
    private final String description;
}
