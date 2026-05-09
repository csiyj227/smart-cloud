package com.smart.nl2sql.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class TableMetaVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String tableName;
    private String tableComment;
    /** TABLE or VIEW */
    private String tableType;
    private List<ColumnMetaVO> columns;
}