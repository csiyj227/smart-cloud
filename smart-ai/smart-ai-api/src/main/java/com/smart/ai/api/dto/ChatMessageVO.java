package com.smart.ai.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Server-Sent Event payload for streaming chat response.
 */
@Data
public class ChatMessageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Conversation ID */
    private Long conversationId;

    /** Message ID */
    private Long messageId;

    /** Incremental text content */
    private String content;

    /** Reasoning/thinking content for deep-think models */
    private String reasoningContent;

    /** Event type: CONTENT, REASONING, SEARCH, TOOL_CALL, DONE, ERROR */
    private String eventType;

    /** Model code that generated this response */
    private String modelCode;

    /** Token usage (only in DONE event) */
    private Integer totalTokens;

    /** Error message (only in ERROR event) */
    private String errorMsg;
}
