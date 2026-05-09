package com.smart.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * User create/update form DTO.
 * Includes roleIds for user-role association management.
 *
 * 用户创建/更新表单 DTO。
 * 包含 roleIds 用于用户-角色关联管理。
 */
@Data
public class UserForm {

    private Long userId;

    @NotBlank(message = "Username is required")
    private String username;

    private String password;

    @NotBlank(message = "Real name is required")
    private String realName;

    private String phone;
    private String email;
    private Long deptId;
    private Long postId;
    private String status;
    private List<Long> roleIds;
}