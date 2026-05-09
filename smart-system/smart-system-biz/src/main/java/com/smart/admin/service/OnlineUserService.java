package com.smart.admin.service;

import com.smart.common.core.tenant.TenantContext;
import com.smart.common.security.service.SmartUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Online user service.
 * Tracks currently logged-in users and allows force logout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ONLINE_USER_PREFIX = "online:user:";
    private static final String ONLINE_TOKEN_PREFIX = "online:token:";
    private static final String USER_TOKENS_PREFIX = "online:user_tokens:";

    @Value("${smart.security.token.expiration-hours:12}")
    private int tokenExpirationHours;

    /**
     * Save online user info when they login.
     *
     * @param token     access token
     * @param user      authenticated SmartUser
     * @param ip        client IP (may be null)
     * @param userAgent client user-agent (may be null)
     */
    public void saveOnlineUser(String token, SmartUser user, String ip, String userAgent) {
        String userKey = ONLINE_USER_PREFIX + user.getTenantId() + ":" + user.getUserId();
        String tokenKey = ONLINE_TOKEN_PREFIX + hash(token);
        String userTokensKey = USER_TOKENS_PREFIX + user.getTenantId() + ":" + user.getUserId();

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getUserId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("tenantId", user.getTenantId());
        userInfo.put("deptId", user.getDeptId());
        userInfo.put("loginTime", LocalDateTime.now().toString());
        userInfo.put("ip", ip != null && !ip.isBlank() ? ip : "unknown");
        userInfo.put("userAgent", userAgent != null && !userAgent.isBlank() ? userAgent : "unknown");
        userInfo.put("tokenHash", hash(token));

        // Store user info
        redisTemplate.opsForValue().set(userKey, userInfo, Duration.ofHours(tokenExpirationHours));

        // Store token -> user mapping
        redisTemplate.opsForValue().set(tokenKey, user.getUserId(), Duration.ofHours(tokenExpirationHours));

        // Store user's tokens set
        redisTemplate.opsForSet().add(userTokensKey, tokenKey);
        redisTemplate.expire(userTokensKey, Duration.ofHours(tokenExpirationHours));

        log.info("User logged in: {} in tenant {} from {}", user.getUsername(), user.getTenantId(), ip);
    }

    /**
     * Backward-compatible overload (no IP / UA).
     */
    public void saveOnlineUser(String token, SmartUser user) {
        saveOnlineUser(token, user, null, null);
    }

    /**
     * Remove online entry on logout (by userId + tenantId, no Redis scan).
     */
    public void removeOnlineUser(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return;
        }
        String userKey = ONLINE_USER_PREFIX + tenantId + ":" + userId;
        String userTokensKey = USER_TOKENS_PREFIX + tenantId + ":" + userId;

        Set<Object> tokenKeys = redisTemplate.opsForSet().members(userTokensKey);
        if (tokenKeys != null) {
            for (Object tokenKey : tokenKeys) {
                redisTemplate.delete(tokenKey.toString());
            }
        }
        redisTemplate.delete(userKey);
        redisTemplate.delete(userTokensKey);
        log.info("User logged out: tenant={}, userId={}", tenantId, userId);
    }

    /**
     * Get all online users for current tenant.
     *
     * <p>租户 ID 兜底顺序：
     * <ol>
     *   <li>{@link TenantContext}（由 {@code TenantContextFilter} 从 X-Tenant-Id header 注入）</li>
     *   <li>当前登录 JWT 的 {@code tenant_id} claim（前端只带 Authorization 不带 X-Tenant-Id 时的常见情况）</li>
     * </ol>
     */
    public List<Map<String, Object>> getOnlineUsers() {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            log.debug("getOnlineUsers: tenantId is null (no header, no JWT claim), returning empty list");
            return Collections.emptyList();
        }
        Set<String> userKeys = redisTemplate.keys(ONLINE_USER_PREFIX + tenantId + ":*");

        if (userKeys == null || userKeys.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> onlineUsers = new ArrayList<>();
        for (String key : userKeys) {
            Object userInfo = redisTemplate.opsForValue().get(key);
            if (userInfo instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> info = new HashMap<>((Map<String, Object>) userInfo);
                info.put("key", key);
                onlineUsers.add(info);
            }
        }

        return onlineUsers.stream()
                .sorted((a, b) -> {
                    String timeA = (String) a.get("loginTime");
                    String timeB = (String) b.get("loginTime");
                    return timeB.compareTo(timeA);
                })
                .collect(Collectors.toList());
    }

    /**
     * Check if user is online.
     */
    public boolean isUserOnline(Long userId) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            return false;
        }
        String userKey = ONLINE_USER_PREFIX + tenantId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(userKey));
    }

    /**
     * Force logout a user (kick them offline).
     */
    public void forceLogout(Long userId) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            log.warn("forceLogout({}): tenantId is null, abort", userId);
            return;
        }
        String userKey = ONLINE_USER_PREFIX + tenantId + ":" + userId;
        String userTokensKey = USER_TOKENS_PREFIX + tenantId + ":" + userId;

        // Get all tokens for this user
        Set<Object> tokenKeys = redisTemplate.opsForSet().members(userTokensKey);

        if (tokenKeys != null) {
            for (Object tokenKey : tokenKeys) {
                redisTemplate.delete(tokenKey.toString());
            }
        }

        // Delete user info and tokens set
        redisTemplate.delete(userKey);
        redisTemplate.delete(userTokensKey);

        log.warn("Force logout user: {} in tenant {}", userId, tenantId);
    }

    /**
     * Force logout by token.
     */
    public void forceLogoutByToken(String token) {
        String tokenKey = ONLINE_TOKEN_PREFIX + hash(token);
        Object userIdObj = redisTemplate.opsForValue().get(tokenKey);

        if (userIdObj != null) {
            Long userId = Long.parseLong(userIdObj.toString());
            forceLogout(userId);
        } else {
            // Just delete the token
            redisTemplate.delete(tokenKey);
        }
    }

    /**
     * Remove expired online users (cleanup).
     */
    public void cleanupExpiredUsers() {
        Set<String> userKeys = redisTemplate.keys(ONLINE_USER_PREFIX + "*");

        if (userKeys == null || userKeys.isEmpty()) {
            return;
        }

        int cleaned = 0;
        for (String key : userKeys) {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                redisTemplate.delete(key);
                cleaned++;
            }
        }

        log.debug("Cleaned up {} expired online user entries", cleaned);
    }

    /**
     * Get online user count for current tenant.
     */
    public long getOnlineUserCount() {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            return 0;
        }
        Set<String> userKeys = redisTemplate.keys(ONLINE_USER_PREFIX + tenantId + ":*");
        return userKeys != null ? userKeys.size() : 0;
    }

    /**
     * Update user info (e.g., IP address).
     */
    public void updateUserInfo(Long userId, Map<String, Object> updates) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            return;
        }
        String userKey = ONLINE_USER_PREFIX + tenantId + ":" + userId;

        Object userInfo = redisTemplate.opsForValue().get(userKey);
        if (userInfo instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> info = new HashMap<>((Map<String, Object>) userInfo);
            info.putAll(updates);
            redisTemplate.opsForValue().set(userKey, info, Duration.ofHours(tokenExpirationHours));
        }
    }

    private String hash(String value) {
        return String.valueOf(value.hashCode());
    }

    /**
     * 解析当前请求的租户 ID。
     *
     * <p>优先取 {@link TenantContext}（由 {@code TenantContextFilter} 从
     * {@code X-Tenant-Id} header 注入）；若 header 不存在，则尝试从当前
     * {@link SecurityContextHolder} 的 JWT 中读取 {@code tenant_id} claim。
     *
     * <p>这样设计是为了让 boot 单体模式下，前端不带 X-Tenant-Id 也能正确解析租户。
     */
    private Long resolveTenantId() {
        Long tenantId = TenantContext.get().orElse(null);
        if (tenantId != null) {
            return tenantId;
        }
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                Object claim = jwt.getClaim("tenant_id");
                if (claim instanceof Number n) {
                    return n.longValue();
                }
                if (claim != null) {
                    return Long.parseLong(claim.toString());
                }
            } else if (auth != null && auth.getPrincipal() instanceof SmartUser su) {
                return su.getTenantId();
            }
        } catch (Exception e) {
            log.debug("resolveTenantId fallback failed: {}", e.getMessage());
        }
        return null;
    }
}