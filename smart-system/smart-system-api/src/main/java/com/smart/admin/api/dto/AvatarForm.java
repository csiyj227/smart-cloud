package com.smart.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 仅更新头像 URL（图片本体走 /file/upload 上传后拿到 URL）。
 */
@Data
public class AvatarForm {

    @NotBlank(message = "Avatar URL is required")
    @Size(max = 512, message = "Avatar URL too long")
    private String avatar;
}
