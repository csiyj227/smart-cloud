package com.smart.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前登录用户自助修改密码表单。
 * 必须同时校验旧密码 + 新密码长度，业务层再二次校验旧密码是否匹配。
 */
@Data
public class ChangePasswordForm {

    @NotBlank(message = "Old password is required")
    private String oldPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 64, message = "Password length must be between 6 and 64")
    private String newPassword;
}
