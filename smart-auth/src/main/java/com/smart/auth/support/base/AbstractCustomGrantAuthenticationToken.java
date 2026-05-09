package com.smart.auth.support.base;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

import java.util.Map;

/**
 * Base authentication token for custom OAuth2 grant types.
 * All custom grant tokens (password, sms, etc.) extend this class.
 *
 * 自定义 OAuth2 授权类型的基础认证令牌。
 * 所有自定义授权令牌（密码、短信等）都继承此类。
 */
public class AbstractCustomGrantAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

    private final Authentication clientPrincipal;
    private final Map<String, Object> additionalParameters;

    public AbstractCustomGrantAuthenticationToken(AuthorizationGrantType authorizationGrantType,
                                                   Authentication clientPrincipal,
                                                   Map<String, Object> additionalParameters) {
        super(authorizationGrantType, clientPrincipal, additionalParameters);
        this.clientPrincipal = clientPrincipal;
        this.additionalParameters = additionalParameters;
    }

    @Override
    public Object getPrincipal() {
        return clientPrincipal;
    }

    public Map<String, Object> getAdditionalParameters() {
        return additionalParameters;
    }
}