package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Dynamic form entity.
 * Stores form schema for dynamic form generation.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_form")
@TenantEntity
public class SysForm extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long formId;

    /**
     * Form name
     */
    private String formName;

    /**
     * Form key (unique identifier)
     */
    private String formKey;

    /**
     * Form schema (JSON)
     */
    private String schema;

    /**
     * Form layout (JSON)
     */
    private String layout;

    /**
     * Form description
     */
    private String description;

    /**
     * Form category
     */
    private String category;

    /**
     * Status: 0=draft, 1=published
     */
    private String status;

    /**
     * Version number
     */
    private Integer version;

    /**
     * Table name for data storage (optional)
     */
    private String tableName;
}