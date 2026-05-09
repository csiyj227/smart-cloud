package com.smart.nl2sql.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class Nl2SqlChatCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long sessionId;

    @NotNull(message = "数据集不能为空")
    private Long datasetId;

    @NotBlank(message = "问题不能为空")
    private String question;

    private Long modelId;
}