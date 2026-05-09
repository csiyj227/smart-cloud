package com.smart.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前登录用户「编辑个人基本资料」的表单。
 * 注意：username/userType/status/lockFlag/deptId/postId/tenantId 等敏感字段
 * 一律不允许通过此接口修改，避免越权。
 */
@Data
public class ProfileForm {

    @NotBlank(message = "Real name is required")
    @Size(max = 64, message = "Real name too long")
    private String realName;

    @Size(max = 32, message = "Phone too long")
    private String phone;

    @Size(max = 128, message = "Email too long")
    private String email;

    @Size(max = 512, message = "Avatar URL too long")
    private String avatar;
}
