package com.smart.flow.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

import org.springframework.dao.DataAccessException;

/**
 * Thin Redis facade caching every user's pending-task count - the only number the front-end
 * polls every few seconds to update its tray badge.
 *
 * <p>The hot path (badge poll) hits Redis only; on a miss the caller falls back to a
 * {@code COUNT(*)} on {@code flow_task_view} and writes the result back. Writes go through
 * {@link #evict(Long)} from the projector so the badge is correct within one event-loop tick
 * after a task is assigned/completed.
 *
 * <p>Choice of TTL is intentionally short (5 minutes) - badge accuracy matters far more than
 * cache hit ratio, and the projector evicts on every relevant event anyway, so the TTL is
 * really just a safety net for events that may have been missed (e.g. a crashed JVM
 * mid-projection).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingCountCache {

    private static final String KEY_PREFIX = "flow:pending:count:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    /**
     * Returns {@code null} on either a true cache miss or a cache fault (Redis down,
     * malformed value left over from a previous schema). The caller treats {@code null}
     * as "go to the database", so a cache outage degrades latency rather than availability.
     */
    public Long get(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            String raw = redisTemplate.opsForValue().get(key(userId));
            if (raw == null) {
                return null;
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            // A poisoned value is treated as a miss; the caller will refresh it.
            log.warn("Discarding malformed pending-count cache entry for user {}: {}", userId, ex.getMessage());
            redisTemplate.delete(key(userId));
            return null;
        } catch (DataAccessException ex) {
            log.warn("Pending-count cache read failed for user {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    public void put(Long userId, long count) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(userId), Long.toString(count), TTL);
        } catch (DataAccessException ex) {
            log.warn("Pending-count cache write failed for user {}: {}", userId, ex.getMessage());
        }
    }

    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.delete(key(userId));
        } catch (DataAccessException ex) {
            log.warn("Pending-count cache evict failed for user {}: {}", userId, ex.getMessage());
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
