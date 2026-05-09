package com.smart.auth.support.base;

import lombok.extern.slf4j.Slf4j;
import com.smart.common.security.service.SmartUser;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.Map;

/**
 * Base authentication provider for custom OAuth2 grant types.
 * Handles the common flow: validate client → authenticate user → generate tokens.
 *
 * 自定义 OAuth2 授权类型的基础认证提供者。
 * 处理通用流程：验证客户端 → 认证用户 → 生成令牌。
 */
@Slf4j
public abstract class AbstractCustomGrantAuthenticationProvider implements AuthenticationProvider {

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    protected AbstractCustomGrantAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        AbstractCustomGrantAuthenticationToken grantToken = (AbstractCustomGrantAuthenticationToken) authentication;

        Object principal = grantToken.getPrincipal();
        log.info("[AUTH] Custom grant authenticate called, principal type={}, principal={}",
                principal != null ? principal.getClass().getSimpleName() : "null",
                principal);

        // Get registered client from the client authentication
        OAuth2ClientAuthenticationToken clientAuth = null;
        if (grantToken.getPrincipal() instanceof OAuth2ClientAuthenticationToken) {
            clientAuth = (OAuth2ClientAuthenticationToken) grantToken.getPrincipal();
        }

        if (clientAuth == null || !clientAuth.isAuthenticated()) {
            log.warn("[AUTH] Client authentication failed: clientAuth={}, authenticated={}",
                    clientAuth != null ? "present" : "null",
                    clientAuth != null && clientAuth.isAuthenticated());
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }

        RegisteredClient registeredClient = clientAuth.getRegisteredClient();
        if (registeredClient == null) {
            log.warn("[AUTH] RegisteredClient is null");
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }

        log.info("[AUTH] Client authenticated: clientId={}", registeredClient.getClientId());

        // Check if client supports this grant type
        checkClient(registeredClient);

        // Authenticate the user via subclass implementation
        Authentication userAuth = buildUserAuthentication(grantToken.getAdditionalParameters());
        if (userAuth == null || !userAuth.isAuthenticated()) {
            log.warn("[AUTH] User authentication failed");
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.ACCESS_DENIED);
        }

        log.info("[AUTH] User authenticated: {}", userAuth.getName());

        // Build token context
        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userAuth)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizationGrantType(getGrantType())
                .authorizedScopes(registeredClient.getScopes());

        // Generate access token
        OAuth2AccessToken accessToken = generateAccessToken(tokenContextBuilder);

        // Generate refresh token
        OAuth2RefreshToken refreshToken = generateRefreshToken(tokenContextBuilder);

        // Build and save authorization
        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(userAuth.getName())
                .authorizationGrantType(getGrantType())
                .attribute("java.security.Principal", userAuth.getPrincipal());

        if (userAuth.getPrincipal() instanceof SmartUser smartUser) {
            authorizationBuilder
                    .attribute("user_id", smartUser.getUserId())
                    .attribute("tenant_id", smartUser.getTenantId())
                    .attribute("dept_id", smartUser.getDeptId())
                    .attribute("username", smartUser.getUsername())
                    .attribute("real_name", smartUser.getRealName())
                    .attribute("phone", smartUser.getPhone())
                    .attribute("avatar", smartUser.getAvatar())
                    .attribute("authorities", smartUser.getAuthorities().stream()
                            .map(grantedAuthority -> grantedAuthority.getAuthority())
                            .toList());
        }

        if (accessToken != null) {
            authorizationBuilder.accessToken(accessToken);
        }
        if (refreshToken != null) {
            authorizationBuilder.refreshToken(refreshToken);
        }

        OAuth2Authorization authorization = authorizationBuilder.build();
        authorizationService.save(authorization);

        log.info("[AUTH] Token generated successfully for user={}", userAuth.getName());

        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient, userAuth, accessToken, refreshToken, Map.of());
    }

    private OAuth2AccessToken generateAccessToken(DefaultOAuth2TokenContext.Builder builder) {
        DefaultOAuth2TokenContext context = builder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
        OAuth2Token generatedToken = tokenGenerator.generate(context);
        if (generatedToken instanceof OAuth2AccessToken accessToken) {
            return accessToken;
        }
        if (generatedToken != null) {
            return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                    generatedToken.getTokenValue(), generatedToken.getIssuedAt(),
                    generatedToken.getExpiresAt(), context.getAuthorizedScopes());
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
    }

    private OAuth2RefreshToken generateRefreshToken(DefaultOAuth2TokenContext.Builder builder) {
        DefaultOAuth2TokenContext context = builder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
        OAuth2Token generatedToken = tokenGenerator.generate(context);
        if (generatedToken instanceof OAuth2RefreshToken refreshToken) {
            return refreshToken;
        }
        return null;
    }

    protected abstract AuthorizationGrantType getGrantType();

    protected abstract Authentication buildUserAuthentication(Map<String, Object> parameters);

    protected abstract void checkClient(RegisteredClient registeredClient);
}