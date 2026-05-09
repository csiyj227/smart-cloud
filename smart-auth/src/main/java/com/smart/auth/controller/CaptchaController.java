package com.smart.auth.controller;

import com.smart.auth.captcha.CaptchaService;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Captcha controller for generating and verifying captcha codes.
 *
 * 验证码控制器，用于生成和验证验证码。
 */
@Slf4j
@RestController
@RequestMapping("/auth/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /**
     * Generate a new captcha.
     * GET /captcha/image
     */
    @GetMapping("/image")
    public ApiResult<CaptchaService.CaptchaResult> generateCaptcha() {
        CaptchaService.CaptchaResult result = captchaService.generate();
        return ApiResult.success(result);
    }

    /**
     * Verify captcha code.
     * POST /captcha/verify
     *
     * @param params contains "uuid" and "code"
     */
    @PostMapping("/verify")
    public ApiResult<Boolean> verifyCaptcha(@RequestBody Map<String, String> params) {
        String uuid = params.get("uuid");
        String code = params.get("code");

        boolean result = captchaService.verify(uuid, code);
        if (!result) {
            return ApiResult.failure("Captcha verification failed");
        }
        return ApiResult.success(true);
    }

    /**
     * Generate sliding captcha (for frontend slider component).
     * GET /captcha/sliding
     */
    @GetMapping("/sliding")
    public ApiResult<CaptchaService.SlidingCaptchaResult> generateSlidingCaptcha() {
        CaptchaService.SlidingCaptchaResult result = captchaService.generateSliding();
        return ApiResult.success(result);
    }

    /**
     * Verify sliding captcha.
     * POST /captcha/sliding/verify
     */
    @PostMapping("/sliding/verify")
    public ApiResult<Boolean> verifySlidingCaptcha(@RequestBody Map<String, Object> params) {
        String uuid = (String) params.get("uuid");
        Object sliderXObj = params.get("sliderX");

        if (uuid == null || sliderXObj == null) {
            return ApiResult.failure("Missing parameters");
        }

        int sliderX;
        if (sliderXObj instanceof Number) {
            sliderX = ((Number) sliderXObj).intValue();
        } else {
            sliderX = Integer.parseInt(sliderXObj.toString());
        }

        boolean result = captchaService.verifySliding(uuid, sliderX);
        if (!result) {
            return ApiResult.failure("Sliding captcha verification failed");
        }
        return ApiResult.success(true);
    }
}