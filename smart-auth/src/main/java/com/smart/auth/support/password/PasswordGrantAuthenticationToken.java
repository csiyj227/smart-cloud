package com.smart.auth.support.password;

import com.smart.auth.support.base.AbstractCustomGrantAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Map;

/**
 * Authentication token for the password grant type.
 *
 * 密码授权类型的认证令牌。
 */
public class PasswordGrantAuthenticationToken extends AbstractCustomGrantAuthenticationToken {

    public static final AuthorizationGrantType GRANT_TYPE =
            new AuthorizationGrantType("password");

    public PasswordGrantAuthenticationToken(Authentication clientPrincipal,
                                             Map<String, Object> additionalParameters) {
        super(GRANT_TYPE, clientPrincipal, additionalParameters);
    }
}