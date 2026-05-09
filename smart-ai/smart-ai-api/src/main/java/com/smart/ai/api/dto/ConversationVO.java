package com.smart.ai.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Conversation view object.
 */
@Data
public class ConversationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private Long modelConfigId;
    private String modelName;
    private Long agentId;
    private String agentName;
    private Integer messageCount;
    private Long totalTokens;
    private Boolean pinned;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
