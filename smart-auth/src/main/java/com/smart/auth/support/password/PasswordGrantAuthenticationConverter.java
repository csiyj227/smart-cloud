package com.smart.auth.support.password;

import com.smart.auth.support.base.AbstractCustomGrantAuthenticationConverter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts password grant parameters from the OAuth2 token request.
 * Expected parameters: username, password, tenant_id (optional), captcha_uuid, captcha_code (optional)
 *
 * 从 OAuth2 令牌请求中提取密码授权参数。
 * 期望参数：username、password、tenant_id（可选）、captcha_uuid、captcha_code（可选）
 */
public class PasswordGrantAuthenticationConverter extends AbstractCustomGrantAuthenticationConverter {

    @Override
    protected AuthorizationGrantType getGrantType() {
        return PasswordGrantAuthenticationToken.GRANT_TYPE;
    }

    @Override
    protected Map<String, Object> extractAdditionalParameters(HttpServletRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("username", request.getParameter("username"));
        params.put("password", request.getParameter("password"));
        String tenantId = request.getParameter("tenant_id");
        if (tenantId != null) {
            params.put("tenant_id", Long.parseLong(tenantId));
        }
        // Captcha parameters (optional)
        String captchaUuid = request.getParameter("captcha_uuid");
        String captchaCode = request.getParameter("captcha_code");
        if (captchaUuid != null && captchaCode != null) {
            params.put("captcha_uuid", captchaUuid);
            params.put("captcha_code", captchaCode);
        }
        return params;
    }

    @Override
    protected Authentication createToken(Authentication clientPrincipal, Map<String, Object> additionalParameters) {
        return new PasswordGrantAuthenticationToken(clientPrincipal, additionalParameters);
    }
}