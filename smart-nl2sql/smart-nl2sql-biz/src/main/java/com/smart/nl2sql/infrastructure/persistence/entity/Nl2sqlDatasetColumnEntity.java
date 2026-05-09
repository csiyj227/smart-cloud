package com.smart.nl2sql.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nl2sql_dataset_column")
public class Nl2sqlDatasetColumnEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;
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