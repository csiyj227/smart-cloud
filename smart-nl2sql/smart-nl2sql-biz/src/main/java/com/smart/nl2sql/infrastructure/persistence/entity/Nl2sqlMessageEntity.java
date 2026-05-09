package com.smart.nl2sql.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nl2sql_message")
public class Nl2sqlMessageEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private String role;
    private String content;
    private String generatedSql;
    private Integer sqlStatus;
    private String queryResult;
    private Integer resultCount;
    private Integer executionTime;
    private String chartConfig;
    private String chartType;
    private String dimensions;
    private String measures;
    private String dataInsight;
    private String errorMessage;
}