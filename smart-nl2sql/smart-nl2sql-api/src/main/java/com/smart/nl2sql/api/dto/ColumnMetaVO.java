package com.smart.nl2sql.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ColumnMetaVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String columnName;
    private String columnType;
    private String columnComment;
    private Boolean isPrimaryKey;
    private Boolean isNullable;
    private String defaultValue;
}