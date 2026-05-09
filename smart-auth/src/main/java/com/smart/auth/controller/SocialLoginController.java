package com.smart.auth.controller;

import com.smart.auth.social.SocialLoginProvider;
import com.smart.auth.social.SocialLoginService;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Social login controller.
 *
 * 社交登录控制器，支持 GitHub、微信、Google 等第三方登录。
 */
@Slf4j
@RestController
@RequestMapping("/auth/social")
@RequiredArgsConstructor
public class SocialLoginController {

    private final SocialLoginService socialLoginService;

    /**
     * Get supported social login providers.
     */
    @GetMapping("/providers")
    public ApiResult<List<Map<String, String>>> getProviders() {
        // This would typically come from configuration
        List<Map<String, String>> providers = List.of(
                Map.of("type", "github", "name", "GitHub", "icon", "github"),
                Map.of("type", "wechat", "name", "WeChat", "icon", "wechat"),
                Map.of("type", "google", "name", "Google", "icon", "google")
        );
        return ApiResult.success(providers);
    }

    /**
     * Get authorization URL for social login.
     * GET /social/authorize/github?redirectUri=xxx
     */
    @GetMapping("/authorize/{provider}")
    public ApiResult<Map<String, String>> authorize(
            @PathVariable String provider,
            @RequestParam(required = false) String redirectUri) {

        String state = UUID.randomUUID().toString();
        String authUrl = socialLoginService.getAuthorizationUrl(provider, redirectUri, state);

        Map<String, String> result = new HashMap<>();
        result.put("url", authUrl);
        result.put("state", state);
        return ApiResult.success(result);
    }

    /**
     * Handle social login callback.
     * POST /social/callback/github
     */
    @PostMapping("/callback/{provider}")
    public ApiResult<SocialLoginService.SocialUserInfo> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            @RequestParam(required = false) Long tenantId) {

        try {
            SocialLoginService.SocialUserInfo userInfo = socialLoginService.handleCallback(provider, code, state);
            userInfo.setProviderType(provider);

            // Check if account is bound
            boolean isBound = socialLoginService.isSocialAccountBound(provider, userInfo.getOpenId());

            Map<String, Object> result = new HashMap<>();
            result.put("userInfo", userInfo);
            result.put("bound", isBound);

            if (isBound) {
                Long userId = socialLoginService.getUserIdBySocialAccount(provider, userInfo.getOpenId());
                result.put("userId", userId);
            }

            return ApiResult.success(userInfo);
        } catch (Exception e) {
            log.error("Social callback failed", e);
            return ApiResult.failure("Social login failed: " + e.getMessage());
        }
    }

    /**
     * Bind social account to current user.
     * POST /social/bind
     */
    @PostMapping("/bind")
    public ApiResult<Void> bind(
            @RequestParam String provider,
            @RequestParam String openId,
            @RequestParam Long userId) {
        socialLoginService.bindSocialAccount(userId, provider, openId);
        return ApiResult.success();
    }

    /**
     * Unbind social account from current user.
     * POST /social/unbind
     */
    @PostMapping("/unbind")
    public ApiResult<Void> unbind(
            @RequestParam String provider,
            @RequestParam String openId,
            @RequestParam Long userId) {
        socialLoginService.unbindSocialAccount(userId, provider, openId);
        return ApiResult.success();
    }
}