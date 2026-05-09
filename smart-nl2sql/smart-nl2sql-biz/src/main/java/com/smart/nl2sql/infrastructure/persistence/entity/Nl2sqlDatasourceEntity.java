package com.smart.nl2sql.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nl2sql_datasource")
@TenantEntity
public class Nl2sqlDatasourceEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String type;
    private String host;
    private Integer port;
    private String databaseName;
    private String schemaName;
    private String username;
    private String password;
    private String extraParams;
    private Integer status;
    private String description;
    private LocalDateTime lastTestTime;
    private Integer lastTestStatus;
}