package com.smart.auth.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redis-backed OAuth2AuthorizationService implementation.
 * Stores OAuth2Authorization objects in Redis with TTL support.
 *
 * 基于 Redis 的 OAuth2AuthorizationService 实现。
 * 将 OAuth2Authorization 对象存储在 Redis 中，支持 TTL 过期。
 */
@Slf4j
@Component
public class RedisOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private static final String AUTHORIZATION_KEY_PREFIX = "oauth2:authorization:";
    private static final String ACCESS_TOKEN_KEY_PREFIX = "oauth2:access_token:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "oauth2:refresh_token:";
    private static final String CODE_KEY_PREFIX = "oauth2:code:";
    private static final String AUTHORIZATION_CODE_KEY_SET_KEY = "oauth2:code_keys";

    private static final Duration DEFAULT_ACCESS_TOKEN_LIFETIME = Duration.ofHours(12);
    private static final Duration DEFAULT_REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);
    private static final Duration DEFAULT_CODE_LIFETIME = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RegisteredClientRepository registeredClientRepository;
    /**
     * 专供 OAuth2 序列化使用的 ObjectMapper（注册了 SecurityJackson2Modules）。
     * 不再共用全局 Mapper，避免 Web 层响应被 OAuth2 的序列化策略影响。
     */
    private final ObjectMapper objectMapper;

    public RedisOAuth2AuthorizationService(RedisTemplate<String, Object> redisTemplate,
                                           RegisteredClientRepository registeredClientRepository,
                                           @Qualifier(AuthJacksonConfiguration.OAUTH2_OBJECT_MAPPER) ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.registeredClientRepository = registeredClientRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");

        String authorizationKey = getAuthorizationKey(authorization.getId());
        redisTemplate.opsForValue().set(authorizationKey, toJson(authorization), getAuthorizationLifetime(authorization));

        // Index by token values for quick lookup
        if (authorization.getAccessToken() != null) {
            String accessTokenKey = getAccessTokenKey(authorization.getAccessToken().getToken().getTokenValue());
            redisTemplate.opsForValue().set(accessTokenKey, authorization.getId(), getAccessTokenLifetime(authorization));
        }

        if (authorization.getRefreshToken() != null) {
            String refreshTokenKey = getRefreshTokenKey(authorization.getRefreshToken().getToken().getTokenValue());
            redisTemplate.opsForValue().set(refreshTokenKey, authorization.getId(), getRefreshTokenLifetime(authorization));
        }

        // Handle authorization code
        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode =
                authorization.getToken(OAuth2AuthorizationCode.class);
        if (authorizationCode != null && authorizationCode.getToken() != null) {
            String codeKey = getCodeKey(authorizationCode.getToken().getTokenValue());
            redisTemplate.opsForValue().set(codeKey, authorization.getId(), DEFAULT_CODE_LIFETIME);
            redisTemplate.opsForSet().add(AUTHORIZATION_CODE_KEY_SET_KEY, codeKey);
        }

        log.debug("Saved OAuth2 authorization: {}", authorization.getId());
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");

        // Remove access token index
        if (authorization.getAccessToken() != null) {
            String accessTokenKey = getAccessTokenKey(authorization.getAccessToken().getToken().getTokenValue());
            redisTemplate.delete(accessTokenKey);
        }

        // Remove refresh token index
        if (authorization.getRefreshToken() != null) {
            String refreshTokenKey = getRefreshTokenKey(authorization.getRefreshToken().getToken().getTokenValue());
            redisTemplate.delete(refreshTokenKey);
        }

        // Remove authorization code index
        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode =
                authorization.getToken(OAuth2AuthorizationCode.class);
        if (authorizationCode != null && authorizationCode.getToken() != null) {
            String codeKey = getCodeKey(authorizationCode.getToken().getTokenValue());
            redisTemplate.delete(codeKey);
        }

        // Remove authorization
        String authorizationKey = getAuthorizationKey(authorization.getId());
        redisTemplate.delete(authorizationKey);

        log.debug("Removed OAuth2 authorization: {}", authorization.getId());
    }

    @Override
    @Nullable
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");

        String authorizationKey = getAuthorizationKey(id);
        Object json = redisTemplate.opsForValue().get(authorizationKey);
        if (json == null) {
            return null;
        }
        return fromJson(json.toString());
    }

    @Override
    @Nullable
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");

        String tokenKey;
        if (tokenType == null) {
            // Try all token types
            tokenKey = getAccessTokenKey(token);
            Object id = redisTemplate.opsForValue().get(tokenKey);
            if (id == null) {
                tokenKey = getRefreshTokenKey(token);
                id = redisTemplate.opsForValue().get(tokenKey);
            }
            if (id == null) {
                tokenKey = getCodeKey(token);
                id = redisTemplate.opsForValue().get(tokenKey);
            }
            if (id == null) {
                return null;
            }
            return findById(id.toString());
        }

        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            tokenKey = getAccessTokenKey(token);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            tokenKey = getRefreshTokenKey(token);
        } else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            tokenKey = getCodeKey(token);
        } else {
            return null;
        }

        Object id = redisTemplate.opsForValue().get(tokenKey);
        if (id == null) {
            return null;
        }
        return findById(id.toString());
    }

    private String getAuthorizationKey(String id) {
        return AUTHORIZATION_KEY_PREFIX + id;
    }

    private String getAccessTokenKey(String token) {
        return ACCESS_TOKEN_KEY_PREFIX + hash(token);
    }

    private String getRefreshTokenKey(String token) {
        return REFRESH_TOKEN_KEY_PREFIX + hash(token);
    }

    private String getCodeKey(String code) {
        return CODE_KEY_PREFIX + hash(code);
    }

    private String hash(String value) {
        return String.valueOf(value.hashCode());
    }

    private Duration getAuthorizationLifetime(OAuth2Authorization authorization) {
        if (authorization.getAccessToken() != null) {
            OAuth2AccessToken accessToken = authorization.getAccessToken().getToken();
            if (accessToken.getExpiresAt() != null) {
                return Duration.between(Instant.now(), accessToken.getExpiresAt()).plusMinutes(1);
            }
        }
        return DEFAULT_ACCESS_TOKEN_LIFETIME;
    }

    private Duration getAccessTokenLifetime(OAuth2Authorization authorization) {
        if (authorization.getAccessToken() != null) {
            OAuth2AccessToken accessToken = authorization.getAccessToken().getToken();
            if (accessToken.getExpiresAt() != null) {
                return Duration.between(Instant.now(), accessToken.getExpiresAt()).plusMinutes(1);
            }
        }
        return DEFAULT_ACCESS_TOKEN_LIFETIME;
    }

    private Duration getRefreshTokenLifetime(OAuth2Authorization authorization) {
        if (authorization.getRefreshToken() != null) {
            OAuth2RefreshToken refreshToken = authorization.getRefreshToken().getToken();
            if (refreshToken.getExpiresAt() != null) {
                return Duration.between(Instant.now(), refreshToken.getExpiresAt()).plusMinutes(1);
            }
        }
        return DEFAULT_REFRESH_TOKEN_LIFETIME;
    }

    private String toJson(OAuth2Authorization authorization) {
        try {
            return objectMapper.writeValueAsString(new OAuth2AuthorizationWrapper(authorization, registeredClientRepository));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize OAuth2Authorization", e);
        }
    }

    private OAuth2Authorization fromJson(String json) {
        try {
            OAuth2AuthorizationWrapper wrapper = objectMapper.readValue(json, OAuth2AuthorizationWrapper.class);
            return wrapper.toAuthorization(registeredClientRepository);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize OAuth2Authorization", e);
        }
    }

    /**
     * Clean up expired authorization codes.
     * Should be called periodically (e.g., via scheduled task).
     */
    public void cleanupExpiredCodes() {
        Set<Object> codeKeys = redisTemplate.opsForSet().members(AUTHORIZATION_CODE_KEY_SET_KEY);
        if (codeKeys == null || codeKeys.isEmpty()) {
            return;
        }

        List<String> toRemove = new ArrayList<>();
        for (Object key : codeKeys) {
            if (!redisTemplate.hasKey(key.toString())) {
                toRemove.add(key.toString());
            }
        }

        if (!toRemove.isEmpty()) {
            redisTemplate.opsForSet().remove(AUTHORIZATION_CODE_KEY_SET_KEY, toRemove.toArray());
        }
    }
}