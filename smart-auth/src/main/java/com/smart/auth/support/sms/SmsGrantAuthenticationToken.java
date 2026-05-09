package com.smart.auth.support.sms;

import com.smart.auth.support.base.AbstractCustomGrantAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Map;

/**
 * Authentication token for the SMS grant type.
 *
 * 短信验证码授权类型的认证令牌。
 */
public class SmsGrantAuthenticationToken extends AbstractCustomGrantAuthenticationToken {

    public static final AuthorizationGrantType GRANT_TYPE =
            new AuthorizationGrantType("sms");

    public SmsGrantAuthenticationToken(Authentication clientPrincipal,
                                        Map<String, Object> additionalParameters) {
        super(GRANT_TYPE, clientPrincipal, additionalParameters);
    }
}