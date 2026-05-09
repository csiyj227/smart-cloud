package com.smart.nl2sql.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nl2sql_dataset_table")
public class Nl2sqlDatasetTableEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;
    private String tableName;
    private String tableComment;
    private String tableAlias;
}