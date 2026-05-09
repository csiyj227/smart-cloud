package com.smart.common.core.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

/**
 * Registry of all Redis cache key patterns used by the platform.
 *
 * <p>Each entry knows its own key prefix and default TTL,
 * eliminating scattered magic strings across the codebase.
 */
@Getter
@AllArgsConstructor
public enum CacheKeyRegistry {

    GATEWAY_ROUTES       ("smart:gw:routes",       Duration.ZERO),
    GATEWAY_ROUTE_STREAM ("smart:gw:route-stream", Duration.ZERO),
    OAUTH2_AUTH          ("smart:oauth2:auth",     Duration.ZERO),
    OAUTH2_CONSENT       ("smart:oauth2:consent",  Duration.ZERO),
    CAPTCHA              ("smart:captcha:",         Duration.ofSeconds(120)),
    PWD_RETRY            ("smart:pwd:retry:",       Duration.ofSeconds(600)),
    DICT                 ("smart:dict:",            Duration.ofHours(2)),
    SYS_CONFIG           ("smart:config:",          Duration.ofHours(2)),
    IDEMPOTENT           ("smart:dedup:",           Duration.ofSeconds(10)),
    MENU_PERMISSION      ("smart:menu:",            Duration.ofHours(1));

    /** Key prefix stored in Redis. */
    private final String prefix;

    /** Default TTL; {@link Duration#ZERO} means no expiration. */
    private final Duration defaultTtl;

    /**
     * Build a full cache key by appending the given suffix to the prefix.
     *
     * @param suffix business identifier (e.g. username, tenant-id)
     * @return full Redis key, e.g. "smart:captcha:abc-uuid"
     */
    public String key(String suffix) {
        return prefix + suffix;
    }

    /**
     * Return the TTL in seconds, or -1 if no expiration.
     */
    public long ttlSeconds() {
        return defaultTtl.isZero() ? -1 : defaultTtl.toSeconds();
    }

    /** Max password retry attempts before lockout. */
    public static final int PWD_RETRY_MAX = 5;
}
