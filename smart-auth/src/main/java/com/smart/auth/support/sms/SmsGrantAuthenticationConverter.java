package com.smart.auth.support.sms;

import com.smart.auth.support.base.AbstractCustomGrantAuthenticationConverter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts SMS grant parameters from the OAuth2 token request.
 * Expected parameters: phone, code, tenant_id (optional)
 *
 * 从 OAuth2 令牌请求中提取短信验证码授权参数。
 * 期望参数：phone、code、tenant_id（可选）
 */
public class SmsGrantAuthenticationConverter extends AbstractCustomGrantAuthenticationConverter {

    @Override
    protected AuthorizationGrantType getGrantType() {
        return SmsGrantAuthenticationToken.GRANT_TYPE;
    }

    @Override
    protected Map<String, Object> extractAdditionalParameters(HttpServletRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("phone", request.getParameter("phone"));
        params.put("code", request.getParameter("code"));
        String tenantId = request.getParameter("tenant_id");
        if (tenantId != null) {
            params.put("tenant_id", Long.parseLong(tenantId));
        }
        return params;
    }

    @Override
    protected Authentication createToken(Authentication clientPrincipal, Map<String, Object> additionalParameters) {
        return new SmsGrantAuthenticationToken(clientPrincipal, additionalParameters);
    }
}