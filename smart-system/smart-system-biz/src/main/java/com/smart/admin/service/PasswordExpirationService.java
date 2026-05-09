package com.smart.admin.service;

import com.smart.admin.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Password expiration service.
 * Enforces password expiration policy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordExpirationService {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PASSWORD_EXPIRE_CHECK_PREFIX = "auth:password_expire_check:";

    @Value("${smart.security.password.expire-days:90}")
    private int defaultPasswordExpireDays;

    @Value("${smart.security.password.warn-days:7}")
    private int passwordWarnDays;

    /**
     * Check if user's password is expired.
     *
     * @param userId the user ID
     * @return true if password is expired
     */
    public boolean isPasswordExpired(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return false;
        }

        // If password never expires (0)
        if (user.getPasswordExpireDays() != null && user.getPasswordExpireDays() == 0) {
            return false;
        }

        int expireDays = user.getPasswordExpireDays() != null
                ? user.getPasswordExpireDays()
                : defaultPasswordExpireDays;

        // If never changed, check from creation time
        LocalDateTime lastChangeTime = user.getPasswordUpdateTime() != null
                ? user.getPasswordUpdateTime()
                : user.getCreateTime();

        if (lastChangeTime == null) {
            return true; // Force change if unknown
        }

        LocalDateTime expireTime = lastChangeTime.plusDays(expireDays);
        return LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * Check if user's password is about to expire (within warn days).
     *
     * @param userId the user ID
     * @return days until expiration, or -1 if not expiring soon
     */
    public int getDaysUntilExpiration(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return -1;
        }

        if (user.getPasswordExpireDays() != null && user.getPasswordExpireDays() == 0) {
            return -1;
        }

        int expireDays = user.getPasswordExpireDays() != null
                ? user.getPasswordExpireDays()
                : defaultPasswordExpireDays;

        LocalDateTime lastChangeTime = user.getPasswordUpdateTime() != null
                ? user.getPasswordUpdateTime()
                : user.getCreateTime();

        if (lastChangeTime == null) {
            return 0;
        }

        LocalDateTime expireTime = lastChangeTime.plusDays(expireDays);
        LocalDateTime now = LocalDateTime.now();

        if (now.isAfter(expireTime)) {
            return 0; // Already expired
        }

        return (int) Duration.between(now, expireTime).toDays();
    }

    /**
     * Check if password change is required (expired or about to expire).
     *
     * @param userId the user ID
     * @return PasswordChangeStatus
     */
    public PasswordChangeStatus checkPasswordChangeStatus(Long userId) {
        if (isPasswordExpired(userId)) {
            return PasswordChangeStatus.EXPIRED;
        }

        int daysLeft = getDaysUntilExpiration(userId);
        if (daysLeft > 0 && daysLeft <= passwordWarnDays) {
            return PasswordChangeStatus.WARNING;
        }

        return PasswordChangeStatus.NORMAL;
    }

    /**
     * Record password change and update timestamp.
     *
     * @param userId the user ID
     */
    public void recordPasswordChange(Long userId) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setPasswordUpdateTime(LocalDateTime.now());
        sysUserService.updateById(user);
        log.info("Recorded password change for user: {}", userId);
    }

    /**
     * Check password expiration on login.
     * Returns error message if password expired, null otherwise.
     *
     * @param userId the user ID
     * @return error message or null
     */
    public String checkPasswordExpirationOnLogin(Long userId) {
        // Skip check if recently checked (within 5 minutes)
        String checkKey = PASSWORD_EXPIRE_CHECK_PREFIX + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(checkKey))) {
            return null;
        }

        PasswordChangeStatus status = checkPasswordChangeStatus(userId);
        String result = null;

        switch (status) {
            case EXPIRED -> {
                result = "Password has expired. Please change your password.";
                // Set a flag to force password change
                redisTemplate.opsForValue().set(checkKey + ":force", "1", Duration.ofMinutes(5));
            }
            case WARNING -> {
                int daysLeft = getDaysUntilExpiration(userId);
                result = "Password will expire in " + daysLeft + " days. Please change your password.";
            }
            case NORMAL -> {
                // No action needed
            }
        }

        // Set check flag
        redisTemplate.opsForValue().set(checkKey, "1", Duration.ofMinutes(5));
        return result;
    }

    /**
     * Check if password change is forced (expired).
     *
     * @param userId the user ID
     * @return true if forced
     */
    public boolean isPasswordChangeForced(Long userId) {
        String forceKey = PASSWORD_EXPIRE_CHECK_PREFIX + userId + ":force";
        return Boolean.TRUE.equals(redisTemplate.hasKey(forceKey));
    }

    /**
     * Clear force change flag after password is changed.
     *
     * @param userId the user ID
     */
    public void clearForceChangeFlag(Long userId) {
        String forceKey = PASSWORD_EXPIRE_CHECK_PREFIX + userId + ":force";
        redisTemplate.delete(forceKey);
    }

    public enum PasswordChangeStatus {
        NORMAL,
        WARNING,
        EXPIRED
    }
}