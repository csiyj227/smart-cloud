package com.smart.auth.social;

/**
 * Social login provider interface.
 * Implement this to add support for new social platforms.
 *
 * 社交登录提供者接口。
 * 实现此接口以添加对新社交平台的支持。
 */
public interface SocialLoginProvider {

    /**
     * Get provider type (e.g., "wechat", "github", "google").
     */
    String getProviderType();

    /**
     * Get authorization URL.
     */
    String getAuthorizationUrl(String redirectUri, String state);

    /**
     * Exchange code for user info.
     */
    SocialLoginService.SocialUserInfo getUserInfo(String code);

    /**
     * Get access token from code.
     */
    default String getAccessToken(String code) {
        throw new UnsupportedOperationException("Use getUserInfo instead");
    }

    /**
     * Refresh access token.
     */
    default String refreshAccessToken(String refreshToken) {
        throw new UnsupportedOperationException("Not supported");
    }
}