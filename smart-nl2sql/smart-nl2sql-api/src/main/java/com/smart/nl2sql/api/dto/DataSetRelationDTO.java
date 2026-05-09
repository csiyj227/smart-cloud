package com.smart.nl2sql.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DataSetRelationDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String sourceTable;
    private String sourceColumn;
    private String targetTable;
    private String targetColumn;
    private String relationType;
}