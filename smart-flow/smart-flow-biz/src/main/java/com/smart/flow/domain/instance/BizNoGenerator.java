package com.smart.flow.domain.instance;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Allocates the human-friendly business serial (e.g. {@code LV-20260502-000123}) shown to end
 * users on every approval card.
 *
 * <p>Format: {@code <prefix>-<yyyyMMdd>-<dailySerial>}, where {@code dailySerial} is a
 * zero-padded 6-digit counter that resets at midnight per chart key. The counter lives in
 * Redis under {@code flow:bizno:{chartKey}:{yyyyMMdd}} with a 48-hour TTL, which is plenty of
 * grace period for clock skew while keeping the keyspace bounded.
 *
 * <p>Why Redis rather than a database sequence? The serial is purely a UI affordance - it
 * is allowed to skip values on a redis flush and is not used as a foreign key anywhere -
 * so the simpler/faster implementation wins. If absolute monotonicity ever becomes a
 * requirement, swap this implementation for a JDBC sequence without touching callers.
 */
@Component
@RequiredArgsConstructor
public class BizNoGenerator {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KEY_PREFIX = "flow:bizno:";
    private static final long TTL_HOURS = 48;
    /** Six-digit counter is enough for ~999k starts per chart per day, well above any realistic load. */
    private static final String SERIAL_FORMAT = "%06d";

    private final StringRedisTemplate redisTemplate;

    public String next(String chartKey, String prefix) {
        String day = LocalDate.now().format(DAY_FORMAT);
        String key = KEY_PREFIX + chartKey + ":" + day;
        Long counter = redisTemplate.opsForValue().increment(key);
        if (counter != null && counter == 1L) {
            // Set TTL only on the first hit of the day so we do not extend it on every increment.
            redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        }
        long value = counter == null ? 1L : counter;
        String safePrefix = prefix == null || prefix.isBlank() ? chartKey.toUpperCase() : prefix.toUpperCase();
        return safePrefix + "-" + day + "-" + String.format(SERIAL_FORMAT, value);
    }
}
