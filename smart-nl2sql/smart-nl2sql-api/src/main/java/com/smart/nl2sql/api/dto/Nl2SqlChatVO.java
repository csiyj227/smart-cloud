package com.smart.nl2sql.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Nl2SqlChatVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String type;
    private String content;
    private String sql;
    private String queryResult;
    private Integer resultCount;
    private Long executionTime;
    private String chartType;
    private String chartConfig;
    private String dimensions;
    private String measures;
    private String dataInsight;
    private String errorMessage;
    private Long sessionId;
    private Long messageId;
}