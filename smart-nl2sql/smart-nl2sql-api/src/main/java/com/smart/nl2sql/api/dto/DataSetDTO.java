package com.smart.nl2sql.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class DataSetDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "数据集名称不能为空")
    private String name;

    @NotNull(message = "数据源不能为空")
    private Long datasourceId;

    private String description;
    private List<DataSetTableDTO> tables;
    private List<DataSetRelationDTO> relations;
}