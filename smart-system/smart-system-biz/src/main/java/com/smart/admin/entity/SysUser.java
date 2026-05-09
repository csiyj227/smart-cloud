package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * System user entity.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
@TenantEntity
public class SysUser extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long userId;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String realName;

    private String phone;

    private String avatar;

    private String email;

    private Long deptId;

    private Long postId;

    private Long tenantId;

    private String userType;

    private String status;

    private String lockFlag;

    /**
     * Password last changed time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private java.time.LocalDateTime passwordUpdateTime;

    /**
     * Password expiration days (0 means never expires)
     */
    private Integer passwordExpireDays;

    /**
     * 部门名称（瞬时字段，由 service 层关联 sys_dept 后填充，仅用于查询返回，不参与持久化）。
     * 加在实体上而非新建 VO，是为了避免在 profile/user-info 等多个出口都做一次手动 BeanCopy。
     */
    @TableField(exist = false)
    private String deptName;
}