package com.smart.nl2sql.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class Nl2SqlKnowledgeDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull(message = "数据集不能为空")
    private Long datasetId;

    @NotBlank(message = "知识类型不能为空")
    private String type;

    private String title;

    @NotBlank(message = "知识内容不能为空")
    private String content;
}