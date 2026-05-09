package com.smart.nl2sql.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class Nl2SqlSessionDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;

    @NotNull(message = "数据集不能为空")
    private Long datasetId;

    private Long modelId;
}