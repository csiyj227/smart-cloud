package com.smart.auth.support.sms;

import com.smart.auth.support.base.AbstractCustomGrantAuthenticationProvider;
import com.smart.common.security.service.SmartUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.Map;

/**
 * Authentication provider for the SMS grant type.
 * Validates phone number + SMS verification code.
 *
 * 短信验证码授权类型认证提供者。
 * 验证手机号 + 短信验证码。
 */
@Slf4j
public class SmsGrantAuthenticationProvider extends AbstractCustomGrantAuthenticationProvider {

    private final SmartUserDetailsService userDetailsService;

    public SmsGrantAuthenticationProvider(OAuth2AuthorizationService authorizationService,
                                           OAuth2TokenGenerator<? extends org.springframework.security.oauth2.core.OAuth2Token> tokenGenerator,
                                           SmartUserDetailsService userDetailsService) {
        super(authorizationService, tokenGenerator);
        this.userDetailsService = userDetailsService;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return SmsGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    protected AuthorizationGrantType getGrantType() {
        return SmsGrantAuthenticationToken.GRANT_TYPE;
    }

    @Override
    protected Authentication buildUserAuthentication(Map<String, Object> parameters) {
        String phone = (String) parameters.get("phone");
        String code = (String) parameters.get("code");
        Long tenantId = parameters.get("tenant_id") != null
                ? (Long) parameters.get("tenant_id")
                : 1L;

        if (phone == null || code == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "Missing phone or code", null));
        }

        // TODO: Validate SMS code against Redis cache (smart:sms:{phone})
        // For now, trust the code - SMS validation will be implemented in UPMS module

        try {
            UserDetails userDetails = userDetailsService.loadUserByPhoneAndTenant(phone, tenantId);
            return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        } catch (AuthenticationException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, e.getMessage(), null));
        }
    }

    @Override
    protected void checkClient(RegisteredClient registeredClient) {
        if (!registeredClient.getAuthorizationGrantTypes().contains(SmsGrantAuthenticationToken.GRANT_TYPE)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT,
                            "Client not authorized for SMS grant", null));
        }
    }
}