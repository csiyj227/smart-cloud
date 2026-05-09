package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Dynamic form data entity.
 * Stores submitted form data.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_form_data")
@TenantEntity
public class SysFormData extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Form ID
     */
    private Long formId;

    /**
     * Form key
     */
    private String formKey;

    /**
     * User ID who submitted
     */
    private Long userId;

    /**
     * Submitted data (JSON)
     */
    private String formData;

    /**
     * Submitter IP
     */
    private String ip;

    /**
     * Submitter user agent
     */
    private String userAgent;

    /**
     * Status: 0=draft, 1=submitted
     */
    private String status;
}