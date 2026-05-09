package com.smart.nl2sql.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DataSetColumnDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String tableName;
    private String columnName;
    private String columnType;
    private String columnComment;
    private String userRemark;
    private String sampleValues;
    private Boolean isDimension;
    private Boolean isMeasure;
    private Boolean isPrimaryKey;
    private Integer sortOrder;
}