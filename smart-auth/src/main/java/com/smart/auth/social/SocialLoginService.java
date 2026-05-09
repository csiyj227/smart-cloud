package com.smart.auth.social;

import com.smart.common.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Social login service.
 * Supports multiple social platforms: WeChat, GitHub, Google, etc.
 *
 * 社交登录服务。
 * 支持多个社交平台：微信、GitHub、Google 等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, SocialLoginProvider> providers = new ConcurrentHashMap<>();

    private static final String SOCIAL_BIND_PREFIX = "social:bind:";
    private static final String SOCIAL_STATE_PREFIX = "social:state:";

    /**
     * Register a social login provider.
     */
    public void registerProvider(SocialLoginProvider provider) {
        providers.put(provider.getProviderType(), provider);
        log.info("Registered social login provider: {}", provider.getProviderType());
    }

    /**
     * Get authorization URL for the specified provider.
     */
    public String getAuthorizationUrl(String providerType, String redirectUri, String state) {
        SocialLoginProvider provider = providers.get(providerType);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerType);
        }

        // Store state for verification
        redisTemplate.opsForValue().set(
                SOCIAL_STATE_PREFIX + state,
                providerType,
                Duration.ofMinutes(10)
        );

        return provider.getAuthorizationUrl(redirectUri, state);
    }

    /**
     * Handle callback from social platform.
     * Returns user info if successfully authenticated.
     */
    public SocialUserInfo handleCallback(String providerType, String code, String state) {
        // Verify state
        Object storedState = redisTemplate.opsForValue().get(SOCIAL_STATE_PREFIX + state);
        if (storedState == null || !storedState.equals(providerType)) {
            throw new IllegalStateException("Invalid state parameter");
        }
        redisTemplate.delete(SOCIAL_STATE_PREFIX + state);

        SocialLoginProvider provider = providers.get(providerType);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerType);
        }

        return provider.getUserInfo(code);
    }

    /**
     * Bind social account to existing user.
     */
    public void bindSocialAccount(Long userId, String providerType, String openId) {
        String bindKey = SOCIAL_BIND_PREFIX + providerType + ":" + openId;
        redisTemplate.opsForValue().set(bindKey, userId.toString());
        log.info("Bound social account: provider={}, openId={}, userId={}", providerType, openId, userId);
    }

    /**
     * Unbind social account from user.
     */
    public void unbindSocialAccount(Long userId, String providerType, String openId) {
        String bindKey = SOCIAL_BIND_PREFIX + providerType + ":" + openId;
        redisTemplate.delete(bindKey);
        log.info("Unbound social account: provider={}, openId={}, userId={}", providerType, openId, userId);
    }

    /**
     * Get user ID by social account.
     */
    public Long getUserIdBySocialAccount(String providerType, String openId) {
        String bindKey = SOCIAL_BIND_PREFIX + providerType + ":" + openId;
        Object userId = redisTemplate.opsForValue().get(bindKey);
        return userId != null ? Long.parseLong(userId.toString()) : null;
    }

    /**
     * Check if social account is bound.
     */
    public boolean isSocialAccountBound(String providerType, String openId) {
        String bindKey = SOCIAL_BIND_PREFIX + providerType + ":" + openId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(bindKey));
    }

    /**
     * Get provider by type.
     */
    public SocialLoginProvider getProvider(String providerType) {
        return providers.get(providerType);
    }

    /**
     * Social user info DTO.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SocialUserInfo {
        private String openId;
        private String nickname;
        private String avatar;
        private String email;
        private String gender;
        private String providerType;
    }
}