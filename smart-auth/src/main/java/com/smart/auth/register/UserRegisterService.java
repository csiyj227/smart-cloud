package com.smart.auth.register;

import com.smart.common.core.enums.StatusFlag;
import com.smart.common.core.enums.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * User registration service.
 * Supports self-registration with email/phone verification.
 * Note: Actual user creation is delegated via Feign to SYSTEM service.
 * This service handles verification code generation and validation only.
 *
 * 用户注册服务。
 * 支持通过邮箱/手机验证码自主注册。
 * 注意：实际的用户创建通过 Feign 委托给 SYSTEM 服务完成。
 * 本服务仅处理验证码的生成和校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRegisterService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REGISTER_CODE_PREFIX = "register:code:";
    private static final String REGISTER_LOCK_PREFIX = "register:lock:";
    private static final String REGISTER_USER_PREFIX = "register:user:";

    @Value("${smart.security.register.enabled:true}")
    private boolean registerEnabled;

    @Value("${smart.security.register.code-length:6}")
    private int codeLength;

    @Value("${smart.security.register.code-expires:5}")
    private int codeExpiresMinutes;

    @Value("${smart.security.register.default-tenant-id:1}")
    private Long defaultTenantId;

    /**
     * Check if registration is enabled.
     */
    public boolean isRegisterEnabled() {
        return registerEnabled;
    }

    /**
     * Send registration verification code.
     * Note: In production, should check username availability via SYSTEM Feign API.
     *
     * @param account   email or phone
     * @param type      "email" or "phone"
     * @param tenantId  tenant ID
     */
    public void sendVerifyCode(String account, String type, Long tenantId) {
        if (!registerEnabled) {
            throw new IllegalStateException("Registration is disabled");
        }

        // Check if locked
        String lockKey = REGISTER_LOCK_PREFIX + tenantId + ":" + account;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new IllegalStateException("Too many requests. Please try again later.");
        }

        // Generate code
        String code = generateCode();
        String redisKey = REGISTER_CODE_PREFIX + tenantId + ":" + account;

        redisTemplate.opsForValue().set(redisKey, code, Duration.ofMinutes(codeExpiresMinutes));

        // TODO: Send code via email or SMS
        log.info("Registration code sent to {}: {} (tenant: {})", type, account, tenantId);

        // For development, log the code
        log.debug("Registration code for {}: {}", account, code);
    }

    /**
     * Prepare registration data for user creation.
     * Validates code and stores user data temporarily.
     * Actual user creation should be done by SYSTEM service via Feign.
     *
     * @param username   username
     * @param password   password (encoded)
     * @param code       verification code
     * @param tenantId   tenant ID
     * @param phone      phone (optional)
     * @param email      email (optional)
     * @param realName   real name (optional)
     * @return user data map (to be sent to SYSTEM for creation)
     */
    public UserRegistrationData prepareRegistration(String username, String password, String code,
                                                     Long tenantId, String phone, String email, String realName) {
        if (!registerEnabled) {
            throw new IllegalStateException("Registration is disabled");
        }

        // Validate code
        String redisKey = REGISTER_CODE_PREFIX + tenantId + ":" + username;
        String storedCode = (String) redisTemplate.opsForValue().get(redisKey);

        if (storedCode == null) {
            throw new IllegalArgumentException("Verification code expired");
        }

        if (!storedCode.equals(code)) {
            // Record failure
            String lockKey = REGISTER_LOCK_PREFIX + tenantId + ":" + username;
            redisTemplate.opsForValue().set(lockKey, "1", Duration.ofMinutes(30));
            throw new IllegalArgumentException("Invalid verification code");
        }

        // Delete used code
        redisTemplate.delete(redisKey);

        // Store registration data (will be picked up by SYSTEM or processed here)
        UserRegistrationData userData = new UserRegistrationData();
        userData.setUsername(username);
        userData.setPassword(password);
        userData.setRealName(realName != null ? realName : username);
        userData.setPhone(phone);
        userData.setEmail(email);
        userData.setTenantId(tenantId);
        userData.setStatus(StatusFlag.ENABLED.getValue());
        userData.setUserType(UserType.NORMAL.getValue());

        log.info("User registration prepared: username={}, tenantId={}", username, tenantId);
        return userData;
    }

    /**
     * Verify registration code without registering.
     */
    public boolean verifyCode(String username, String code, Long tenantId) {
        String redisKey = REGISTER_CODE_PREFIX + tenantId + ":" + username;
        String storedCode = (String) redisTemplate.opsForValue().get(redisKey);
        return storedCode != null && storedCode.equals(code);
    }

    private String generateCode() {
        // Generate numeric code
        int code = (int) (Math.random() * Math.pow(10, codeLength));
        return String.format("%0" + codeLength + "d", code);
    }

    /**
     * User registration data DTO.
     */
    public static class UserRegistrationData {
        private String username;
        private String password;
        private String realName;
        private String phone;
        private String email;
        private Long tenantId;
        private String status;
        private String userType;

        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }
    }
}