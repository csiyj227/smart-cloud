package com.smart.common.data.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 跨请求持久化「用户当前激活租户」的存储。
 *
 * <p>背景：{@link com.smart.common.core.tenant.TenantContext} 是 ThreadLocal，
 * 只在单次 HTTP 请求内有效，请求结束就会被 {@code TenantContextHolderFilter}
 * 的 {@code finally { clear() }} 清空。如果直接在 Controller 里调用
 * {@code TenantContext.set(...)} 来"切换租户"，下一次请求
 * 完全感知不到，导致"切换成功但 current 接口返回的还是原租户"。
 *
 * <p>本类用 Redis 按用户 ID 维度存储 override 租户，所有后续请求经过
 * {@code TenantContextHolderFilter} 时会优先读取该 override 替代 header 中的默认租户，
 * 实现真正的「切换」。
 *
 * <p>Redis key 形如：{@code tenant:switch:override:{userId}}，TTL = 30 分钟，
 * 自动随 access_token 生命周期失效，避免长期残留。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSwitchStore {

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "tenant:switch:override:";

    /** 切换租户的过期时间，与 access_token 生命周期对齐 */
    private static final Duration TTL = Duration.ofMinutes(30);

    /**
     * 通过 @Autowired(required=false) 注入，未引入 Redis 的环境降级为 no-op。
     */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 设置某个用户的 override 租户。后续该用户的所有请求都会用此租户替代默认租户。
     */
    public void setOverride(Long userId, Long tenantId) {
        if (userId == null || tenantId == null || redisTemplate == null) {
            return;
        }
        redisTemplate.opsForValue().set(buildKey(userId), tenantId.toString(), TTL);
        log.debug("Tenant override set for user {} → tenant {}", userId, tenantId);
    }

    /**
     * 获取某个用户的 override 租户。无切换时返回 null。
     */
    public Long getOverride(Long userId) {
        if (userId == null || redisTemplate == null) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(buildKey(userId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid tenant override value '{}' for user {}", value, userId);
            return null;
        }
    }

    /**
     * 清除某个用户的 override 租户，恢复到 JWT/header 中的默认租户。
     */
    public void clearOverride(Long userId) {
        if (userId == null || redisTemplate == null) {
            return;
        }
        redisTemplate.delete(buildKey(userId));
        log.debug("Tenant override cleared for user {}", userId);
    }

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
