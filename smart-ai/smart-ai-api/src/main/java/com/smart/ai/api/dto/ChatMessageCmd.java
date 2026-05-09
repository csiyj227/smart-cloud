package com.smart.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Command to send a chat message.
 *
 * 同 ModelProviderCmd：忽略前端回传的额外字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessageCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Conversation ID, null for new conversation */
    private Long conversationId;

    /** Model config ID to use */
    private Long modelConfigId;

    /** Agent ID (optional) */
    private Long agentId;

    /** User message content */
    @NotBlank(message = "Message content cannot be empty")
    private String content;

    /** Enable deep thinking mode */
    private boolean enableDeepThinking;

    /** Enable web search */
    private boolean enableWebSearch;

    /** Image URLs for multimodal input */
    private List<String> imageUrls;
}
