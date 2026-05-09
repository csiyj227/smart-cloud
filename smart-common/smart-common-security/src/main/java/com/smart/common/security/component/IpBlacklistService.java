package com.smart.common.security.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP blacklist service.
 * Blocks malicious IPs from accessing the system.
 *
 * IP 黑名单服务。
 * 阻止恶意 IP 访问系统，支持自动锁定（失败次数超限）和手动加黑/解锁操作，
 * 同时维护 IP 白名单以确保可信地址不受限制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpBlacklistService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String IP_BLACKLIST_PREFIX = "security:ip_blacklist:";
    private static final String IP_WHITELIST_PREFIX = "security:ip_whitelist:";
    private static final String IP_FAIL_COUNT_PREFIX = "security:ip_fail_count:";
    private static final String IP_LOCK_PREFIX = "security:ip_lock:";

    @Value("${smart.security.ip-blacklist.enabled:true}")
    private boolean enabled;

    @Value("${smart.security.ip-blacklist.max-fail-count:10}")
    private int maxFailCount;

    @Value("${smart.security.ip-blacklist.lock-duration:30}")
    private int lockDurationMinutes;

    @Value("${smart.security.ip-whitelist:}")
    private String whitelist;

    private final List<String> whitelistPatterns = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (whitelist != null && !whitelist.isEmpty()) {
            String[] patterns = whitelist.split(",");
            for (String pattern : patterns) {
                String trimmed = pattern.trim();
                if (!trimmed.isEmpty()) {
                    whitelistPatterns.add(trimmed);
                    log.info("Added IP whitelist pattern: {}", trimmed);
                }
            }
        }
    }

    /**
     * Check if IP is blocked.
     */
    public boolean isBlocked(String ip) {
        if (!enabled) {
            return false;
        }

        // Check whitelist first
        if (isWhitelisted(ip)) {
            return false;
        }

        // Check if permanently blacklisted
        String blacklistKey = IP_BLACKLIST_PREFIX + ip;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
            return true;
        }

        // Check if temporarily locked due to too many failures
        String lockKey = IP_LOCK_PREFIX + ip;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            return true;
        }

        return false;
    }

    /**
     * Check if IP is whitelisted.
     */
    public boolean isWhitelisted(String ip) {
        for (String pattern : whitelistPatterns) {
            if (matchesPattern(ip, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Record a failed attempt from IP.
     */
    public void recordFailure(String ip) {
        if (!enabled || isWhitelisted(ip)) {
            return;
        }

        String failCountKey = IP_FAIL_COUNT_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(failCountKey);

        if (count != null && count == 1) {
            redisTemplate.expire(failCountKey, Duration.ofHours(1));
        }

        // Lock if too many failures
        if (count != null && count >= maxFailCount) {
            lockIp(ip);
            log.warn("IP locked due to too many failed attempts: {}", ip);
        }
    }

    /**
     * Clear failure count after successful attempt.
     */
    public void clearFailure(String ip) {
        String failCountKey = IP_FAIL_COUNT_PREFIX + ip;
        redisTemplate.delete(failCountKey);
    }

    /**
     * Add IP to blacklist.
     */
    public void addToBlacklist(String ip, long durationMinutes) {
        String blacklistKey = IP_BLACKLIST_PREFIX + ip;
        redisTemplate.opsForValue().set(blacklistKey, "1", Duration.ofMinutes(durationMinutes));
        log.info("Added IP to blacklist: {}, duration: {} minutes", ip, durationMinutes);
    }

    /**
     * Remove IP from blacklist.
     */
    public void removeFromBlacklist(String ip) {
        String blacklistKey = IP_BLACKLIST_PREFIX + ip;
        redisTemplate.delete(blacklistKey);
        log.info("Removed IP from blacklist: {}", ip);
    }

    /**
     * Lock IP temporarily.
     */
    public void lockIp(String ip) {
        String lockKey = IP_LOCK_PREFIX + ip;
        redisTemplate.opsForValue().set(lockKey, "1", Duration.ofMinutes(lockDurationMinutes));

        // Also delete fail count
        String failCountKey = IP_FAIL_COUNT_PREFIX + ip;
        redisTemplate.delete(failCountKey);
    }

    /**
     * Unlock IP.
     */
    public void unlockIp(String ip) {
        String lockKey = IP_LOCK_PREFIX + ip;
        redisTemplate.delete(lockKey);
        log.info("Unlocked IP: {}", ip);
    }

    /**
     * Get remaining lock time in seconds.
     */
    public long getRemainingLockSeconds(String ip) {
        String lockKey = IP_LOCK_PREFIX + ip;
        Long ttl = redisTemplate.getExpire(lockKey);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    /**
     * Get all blacklisted IPs.
     */
    public List<String> getBlacklistedIps() {
        return getKeys(IP_BLACKLIST_PREFIX);
    }

    /**
     * Get all locked IPs.
     */
    public List<String> getLockedIps() {
        return getKeys(IP_LOCK_PREFIX);
    }

    private List<String> getKeys(String prefix) {
        var keys = redisTemplate.keys(prefix + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(k -> k.toString().replace(prefix, ""))
                .toList();
    }

    private boolean matchesPattern(String ip, String pattern) {
        // Support wildcard patterns like 192.168.*.*
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return ip.matches(regex);
        }
        return ip.equals(pattern);
    }
}