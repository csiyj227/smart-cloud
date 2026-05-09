package com.smart.auth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Password retry lock service.
 * Tracks failed login attempts and locks accounts after max retries.
 *
 * 密码重试锁定服务。
 * 跟踪登录失败次数，超过最大重试次数后锁定账户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordRetryService {

    private static final String RETRY_KEY_PREFIX = "auth:password_retry:";
    private static final String LOCK_KEY_PREFIX = "auth:password_lock:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${smart.security.password-retry.max-retry:5}")
    private int maxRetry;

    @Value("${smart.security.password-retry.lock-duration:30}")
    private int lockDurationMinutes;

    /**
     * Record a failed login attempt.
     *
     * @param username  the username
     * @param tenantId  the tenant ID
     * @return current retry count
     */
    public int recordFailure(String username, Long tenantId) {
        String key = getRetryKey(username, tenantId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // Set expiration on first failure
            redisTemplate.expire(key, Duration.ofHours(24));
        }
        log.debug("Password failure recorded for user {} in tenant {}, count: {}", username, tenantId, count);
        return count != null ? count.intValue() : 1;
    }

    /**
     * Clear failed login attempts after successful login.
     *
     * @param username the username
     * @param tenantId the tenant ID
     */
    public void clearFailures(String username, Long tenantId) {
        String key = getRetryKey(username, tenantId);
        redisTemplate.delete(key);
        log.debug("Password failures cleared for user {} in tenant {}", username, tenantId);
    }

    /**
     * Check if the account is currently locked due to too many failed attempts.
     *
     * @param username the username
     * @param tenantId the tenant ID
     * @return true if locked
     */
    public boolean isLocked(String username, Long tenantId) {
        String key = getLockKey(username, tenantId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Lock the account due to too many failed attempts.
     *
     * @param username the username
     * @param tenantId the tenant ID
     */
    public void lock(String username, Long tenantId) {
        String key = getLockKey(username, tenantId);
        redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(lockDurationMinutes));
        // Also clear the retry counter
        String retryKey = getRetryKey(username, tenantId);
        redisTemplate.delete(retryKey);
        log.warn("Account locked for user {} in tenant {} for {} minutes", username, tenantId, lockDurationMinutes);
    }

    /**
     * Unlock the account.
     *
     * @param username the username
     * @param tenantId the tenant ID
     */
    public void unlock(String username, Long tenantId) {
        String key = getLockKey(username, tenantId);
        redisTemplate.delete(key);
        String retryKey = getRetryKey(username, tenantId);
        redisTemplate.delete(retryKey);
        log.info("Account unlocked for user {} in tenant {}", username, tenantId);
    }

    /**
     * Get remaining lock time in seconds.
     *
     * @param username the username
     * @param tenantId the tenant ID
     * @return remaining seconds, or 0 if not locked
     */
    public long getRemainingLockSeconds(String username, Long tenantId) {
        String key = getLockKey(username, tenantId);
        Long ttl = redisTemplate.getExpire(key);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    /**
     * Check if max retry limit is reached and lock if necessary.
     * Returns true if the account should be locked.
     *
     * @param username the username
     * @param tenantId the tenant ID
     * @return true if max retry reached
     */
    public boolean isMaxRetryReached(String username, Long tenantId) {
        String key = getRetryKey(username, tenantId);
        Object count = redisTemplate.opsForValue().get(key);
        if (count == null) {
            return false;
        }
        return Integer.parseInt(count.toString()) >= maxRetry;
    }

    private String getRetryKey(String username, Long tenantId) {
        return RETRY_KEY_PREFIX + tenantId + ":" + username;
    }

    private String getLockKey(String username, Long tenantId) {
        return LOCK_KEY_PREFIX + tenantId + ":" + username;
    }
}