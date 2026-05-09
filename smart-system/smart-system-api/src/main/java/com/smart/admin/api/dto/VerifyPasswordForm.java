package com.smart.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 锁屏 / 敏感操作前的密码二次校验请求体。
 *
 * <p>历史教训：之前 controller 用 {@code @RequestBody String rawPassword} 接收，
 * 而前端发的是 JSON {"password":"xxx"}，导致 rawPassword 实际拿到整段 JSON 字符串
 * 而不是密码本身，passwordEncoder.matches 永远 false——但前端又把 R&lt;Boolean&gt; 整个
 * 对象当 boolean 用，导致表象变成"输入啥都能解锁"。两个 bug 互相掩盖了 N 周。
 */
@Data
public class VerifyPasswordForm {

    @NotBlank(message = "密码不能为空")
    private String password;
}
