package com.smart.nl2sql.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class DataSetTableDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String tableName;
    private String tableComment;
    private String tableAlias;
    private List<DataSetColumnDTO> columns;
}