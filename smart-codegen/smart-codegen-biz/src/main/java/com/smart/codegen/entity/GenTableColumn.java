package com.smart.codegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@TableName("gen_table_column")
public class GenTableColumn implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tableId;
    private String columnName;
    private String columnComment;
    private String columnType;
    private String javaType;
    private String javaField;
    private Boolean isPk;
    private Boolean isIncrement;
    private Boolean isRequired;
    private Boolean isInsert;
    private Boolean isEdit;
    private Boolean isList;
    private Boolean isQuery;
    private String queryType;
    private String htmlType;
    private String dictType;
    private Integer sortOrder;
}