package com.smart.auth.support.base;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Base authentication converter for custom OAuth2 grant types.
 *
 * 自定义 OAuth2 授权类型的基础认证转换器。
 */
@Slf4j
public abstract class AbstractCustomGrantAuthenticationConverter implements AuthenticationConverter {

    protected abstract AuthorizationGrantType getGrantType();

    protected abstract Map<String, Object> extractAdditionalParameters(HttpServletRequest request);

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!getGrantType().getValue().equals(grantType)) {
            return null;
        }

        log.info("[AUTH-CONVERTER] Converting grant_type={}, client_id={}", grantType, request.getParameter(OAuth2ParameterNames.CLIENT_ID));

        // Client authentication is done by OAuth2ClientAuthenticationFilter before this
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        log.info("[AUTH-CONVERTER] SecurityContext authentication: type={}, authenticated={}, name={}",
                clientPrincipal != null ? clientPrincipal.getClass().getSimpleName() : "null",
                clientPrincipal != null && clientPrincipal.isAuthenticated(),
                clientPrincipal != null ? clientPrincipal.getName() : "null");

        MultiValueMap<String, String> parameters = getParameters(request);
        Map<String, Object> additionalParameters = extractAdditionalParameters(request);

        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (clientId != null) {
            additionalParameters.put(OAuth2ParameterNames.CLIENT_ID, clientId);
        }

        String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
        if (scope != null) {
            additionalParameters.put(OAuth2ParameterNames.SCOPE, scope);
        }

        log.info("[AUTH-CONVERTER] Creating token with clientPrincipal={}", clientPrincipal != null ? clientPrincipal.getClass().getSimpleName() : "null");

        return createToken(clientPrincipal, additionalParameters);
    }

    protected abstract Authentication createToken(Authentication clientPrincipal, Map<String, Object> additionalParameters);

    private MultiValueMap<String, String> getParameters(HttpServletRequest request) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) {
                parameters.add(key, values[0]);
            }
        });
        return parameters;
    }
}