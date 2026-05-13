package com.smart.auth.controller;

import com.smart.auth.register.UserRegisterService;
import com.smart.auth.register.UserRegisterService.UserRegistrationData;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User registration controller.
 *
 * 用户注册控制器，提供注册功能接口。
 */
@Slf4j
@RestController
@RequestMapping("/auth/register")
@RequiredArgsConstructor
public class UserRegisterController {

    private final UserRegisterService userRegisterService;

    /**
     * Check if registration is enabled.
     */
    @GetMapping("/enabled")
    public ApiResult<Boolean> isEnabled() {
        return ApiResult.success(userRegisterService.isRegisterEnabled());
    }

    /**
     * Send registration verification code.
     * POST /register/send-code
     */
    @PostMapping("/send-code")
    public ApiResult<Void> sendVerifyCode(@RequestBody Map<String, String> params) {
        String account = params.get("account");
        String type = params.get("type"); // "email" or "phone"
        Long tenantId = params.get("tenantId") != null
                ? Long.parseLong(params.get("tenantId"))
                : 1L;

        userRegisterService.sendVerifyCode(account, type, tenantId);
        return ApiResult.success();
    }

    /**
     * Register a new user.
     * POST /register
     * Note: This just prepares the data. Actual user creation should be done by SYSTEM.
     */
    @PostMapping
    public ApiResult<UserRegistrationData> register(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        String code = params.get("code");
        Long tenantId = params.get("tenantId") != null
                ? Long.parseLong(params.get("tenantId"))
                : 1L;
        String phone = params.get("phone");
        String email = params.get("email");
        String realName = params.get("realName");

        // Password should be pre-encoded by client or here
        // For now, assume client sends plain password and we note it needs encoding
        UserRegistrationData userData = userRegisterService.prepareRegistration(
                username, password, code, tenantId, phone, email, realName);
        return ApiResult.success(userData);
    }

    /**
     * Check if username is available.
     * GET /register/check-username?username=xxx&tenantId=1
     * Note: This should call SYSTEM via Feign to check actual availability.
     */
    @GetMapping("/check-username")
    public ApiResult<Boolean> checkUsername(
            @RequestParam String username,
            @RequestParam(defaultValue = "1") Long tenantId) {
        // TODO: Call SYSTEM via Feign to check
        // For now, return true (available)
        return ApiResult.success(true);
    }

    /**
     * Verify code without registering.
     * POST /register/verify-code
     */
    @PostMapping("/verify-code")
    public ApiResult<Boolean> verifyCode(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String code = params.get("code");
        Long tenantId = params.get("tenantId") != null
                ? Long.parseLong(params.get("tenantId"))
                : 1L;

        boolean valid = userRegisterService.verifyCode(username, code, tenantId);
        return ApiResult.success(valid);
    }
}