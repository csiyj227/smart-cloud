package com.smart.auth.support.password;

import com.smart.auth.captcha.CaptchaService;
import com.smart.auth.security.PasswordRetryService;
import com.smart.auth.support.base.AbstractCustomGrantAuthenticationProvider;
import com.smart.common.core.event.LoginEventType;
import com.smart.common.core.event.LoginLogEvent;
import com.smart.common.core.web.HttpRequestHelper;
import com.smart.common.security.service.SmartUser;
import com.smart.common.security.service.SmartUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.Map;

/**
 * Authentication provider for the password grant type.
 * Validates username/password credentials using the SmartUserDetailsService.
 * Includes password retry lock and captcha verification functionality.
 *
 * 密码授权类型认证提供者。
 * 使用 SmartUserDetailsService 验证用户名/密码凭据。
 * 包含密码重试锁定和验证码校验功能。
 */
@Slf4j
public class PasswordGrantAuthenticationProvider extends AbstractCustomGrantAuthenticationProvider {

    private final SmartUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordRetryService passwordRetryService;
    private final CaptchaService captchaService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${smart.captcha.enabled:true}")
    private boolean captchaEnabled;

    public PasswordGrantAuthenticationProvider(OAuth2AuthorizationService authorizationService,
                                                OAuth2TokenGenerator<? extends org.springframework.security.oauth2.core.OAuth2Token> tokenGenerator,
                                                SmartUserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder,
                                                PasswordRetryService passwordRetryService,
                                                CaptchaService captchaService,
                                                ApplicationEventPublisher eventPublisher) {
        super(authorizationService, tokenGenerator);
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.passwordRetryService = passwordRetryService;
        this.captchaService = captchaService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    protected AuthorizationGrantType getGrantType() {
        return PasswordGrantAuthenticationToken.GRANT_TYPE;
    }

    @Override
    protected Authentication buildUserAuthentication(Map<String, Object> parameters) {
        String username = (String) parameters.get("username");
        String rawPassword = (String) parameters.get("password");
        Long tenantId = parameters.get("tenant_id") != null
                ? (Long) parameters.get("tenant_id")
                : 1L;

        log.info("[AUTH-PASSWORD] Authenticating user={}, tenantId={}", username, tenantId);

        if (username == null || rawPassword == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "Missing username or password", null));
        }

        // Verify captcha if enabled
        if (captchaEnabled && parameters.containsKey("captcha_uuid") && parameters.containsKey("captcha_code")) {
            String captchaUuid = (String) parameters.get("captcha_uuid");
            String captchaCode = (String) parameters.get("captcha_code");
            if (!captchaService.verify(captchaUuid, captchaCode)) {
                log.warn("[AUTH-PASSWORD] Captcha verification failed: user={}", username);
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_captcha", "Invalid captcha code", null));
            }
        }

        // Check if account is locked due to too many failed attempts
        if (passwordRetryService.isLocked(username, tenantId)) {
            long remainingSeconds = passwordRetryService.getRemainingLockSeconds(username, tenantId);
            log.warn("[AUTH-PASSWORD] Account locked due to too many failed attempts: user={}, remaining={}s", username, remainingSeconds);
            publishLoginEvent(LoginEventType.ACCOUNT_LOCKED, null, username, tenantId,
                    "Account locked, remaining=" + remainingSeconds + "s", null);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_locked",
                            "Account is locked. Try again in " + (remainingSeconds / 60 + 1) + " minutes.", null));
        }

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsernameAndTenant(username, tenantId);
            log.info("[AUTH-PASSWORD] User loaded: {}, password hash present={}", userDetails.getUsername(), userDetails.getPassword() != null);

            if (!passwordEncoder.matches(rawPassword, userDetails.getPassword())) {
                log.warn("[AUTH-PASSWORD] Password mismatch for user={}", username);
                // Record the failure
                int retryCount = passwordRetryService.recordFailure(username, tenantId);
                // Check if max retry reached
                if (passwordRetryService.isMaxRetryReached(username, tenantId)) {
                    passwordRetryService.lock(username, tenantId);
                    log.warn("[AUTH-PASSWORD] Account locked due to max retries: user={}", username);
                    publishLoginEvent(LoginEventType.ACCOUNT_LOCKED, null, username, tenantId,
                            "Account locked due to too many failed attempts", null);
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error("account_locked",
                                    "Too many failed attempts. Account is now locked for 30 minutes.", null));
                }
                publishLoginEvent(LoginEventType.LOGIN_FAILURE, null, username, tenantId,
                        "Invalid credentials", null);
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED,
                                "Invalid credentials. " + (5 - retryCount) + " attempts remaining.", null));
            }
            log.info("[AUTH-PASSWORD] Password matched for user={}", username);
            // Clear failures on successful login
            passwordRetryService.clearFailures(username, tenantId);

            // 发布登录成功事件（监听器异步落库 sys_login_log + 写在线用户）
            Long userId = (userDetails instanceof SmartUser su) ? su.getUserId() : null;
            // 这里 token 还没生成，留给后续登出/刷新事件携带；token 字段先置空
            publishLoginEvent(LoginEventType.LOGIN_SUCCESS, userId, username, tenantId,
                    "Login successful", userDetails);

            return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        } catch (OAuth2AuthenticationException e) {
            // Re-throw OAuth2AuthenticationException
            throw e;
        } catch (AuthenticationException e) {
            log.warn("[AUTH-PASSWORD] Authentication failed for user={}: {}", username, e.getMessage());
            // Record failure for non-OAuth2 exceptions too
            passwordRetryService.recordFailure(username, tenantId);
            publishLoginEvent(LoginEventType.LOGIN_FAILURE, null, username, tenantId,
                    e.getMessage(), null);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, e.getMessage(), null));
        }
    }

    /**
     * 安全发布登录事件：失败也不能影响主流程。
     *
     * @param sourceUser SmartUser，用于让监听器在登录成功时同步写入 OnlineUser 缓存（可为 null）
     */
    private void publishLoginEvent(LoginEventType type, Long userId, String username, Long tenantId,
                                   String msg, Object sourceUser) {
        if (eventPublisher == null) {
            return;
        }
        try {
            String ip = safeGetClientIp();
            String userAgent = safeGetHeader("User-Agent");
            // 登录成功且能拿到 SmartUser 时，把它作为 source，让监听器写在线用户；否则源为 this
            Object source = sourceUser != null ? sourceUser : this;
            eventPublisher.publishEvent(new LoginLogEvent(
                    source, type, userId, username, tenantId, ip, userAgent, msg, null));
        } catch (Exception ex) {
            log.debug("[AUTH-PASSWORD] publish login event failed: {}", ex.getMessage());
        }
    }

    private String safeGetClientIp() {
        try {
            return HttpRequestHelper.clientIp();
        } catch (Exception ignore) {
            return null;
        }
    }

    private String safeGetHeader(String name) {
        try {
            return HttpRequestHelper.header(name).orElse(null);
        } catch (Exception ignore) {
            return null;
        }
    }

    @Override
    protected void checkClient(RegisteredClient registeredClient) {
        if (!registeredClient.getAuthorizationGrantTypes().contains(PasswordGrantAuthenticationToken.GRANT_TYPE)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT,
                            "Client not authorized for password grant", null));
        }
    }
}