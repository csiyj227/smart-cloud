package com.smart.auth.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Instant;
import java.util.*;

/**
 * Jackson-serializable wrapper for OAuth2Authorization.
 * Handles the complex nested structure of OAuth2Authorization.
 *
 * OAuth2Authorization 的 Jackson 可序列化包装器。
 * 处理 OAuth2Authorization 的复杂嵌套结构，支持 Redis 存储和恢复。
 */
public class OAuth2AuthorizationWrapper {

    @JsonProperty("id")
    private String id;

    @JsonProperty("registeredClientId")
    private String registeredClientId;

    @JsonProperty("principalName")
    private String principalName;

    @JsonProperty("authorizationGrantType")
    private String authorizationGrantType;

    @JsonProperty("authorizedScopes")
    private Set<String> authorizedScopes;

    @JsonProperty("attributes")
    private Map<String, Object> attributes;

    @JsonProperty("state")
    private String state;

    @JsonProperty("userAttributes")
    private Map<String, Object> userAttributes;

    @JsonProperty("tokens")
    private Map<String, TokenWrapper> tokens;

    public OAuth2AuthorizationWrapper() {
    }

    public OAuth2AuthorizationWrapper(OAuth2Authorization authorization, RegisteredClientRepository clientRepository) {
        this.id = authorization.getId();
        this.registeredClientId = authorization.getRegisteredClientId();
        this.principalName = authorization.getPrincipalName();
        this.authorizationGrantType = authorization.getAuthorizationGrantType().getValue();
        this.authorizedScopes = new HashSet<>(authorization.getAuthorizedScopes());
        this.attributes = new HashMap<>(authorization.getAttributes());
        this.state = authorization.getAttribute("state");
        this.userAttributes = extractUserAttributes(authorization);

        this.tokens = new HashMap<>();

        // Serialize access token
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        if (accessToken != null) {
            tokens.put("access_token", new TokenWrapper(accessToken));
        }

        // Serialize refresh token
        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
        if (refreshToken != null) {
            tokens.put("refresh_token", new TokenWrapper(refreshToken));
        }

        // Serialize authorization code
        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode = authorization.getToken(OAuth2AuthorizationCode.class);
        if (authorizationCode != null) {
            tokens.put("code", new TokenWrapper(authorizationCode));
        }
    }

    public OAuth2Authorization toAuthorization(RegisteredClientRepository clientRepository) {
        RegisteredClient registeredClient = clientRepository.findById(registeredClientId);
        if (registeredClient == null) {
            throw new IllegalStateException("RegisteredClient not found: " + registeredClientId);
        }

        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(id)
                .principalName(principalName)
                .authorizationGrantType(new org.springframework.security.oauth2.core.AuthorizationGrantType(authorizationGrantType))
                .authorizedScopes(new HashSet<>(authorizedScopes))
                .attributes(attrs -> {
                    if (attributes != null) {
                        attrs.putAll(attributes);
                    }
                    if (userAttributes != null && !userAttributes.isEmpty()) {
                        attrs.putIfAbsent("user_id", userAttributes.get("user_id"));
                        attrs.putIfAbsent("tenant_id", userAttributes.get("tenant_id"));
                        attrs.putIfAbsent("dept_id", userAttributes.get("dept_id"));
                        attrs.putIfAbsent("username", userAttributes.get("username"));
                        attrs.putIfAbsent("real_name", userAttributes.get("real_name"));
                        attrs.putIfAbsent("phone", userAttributes.get("phone"));
                        attrs.putIfAbsent("avatar", userAttributes.get("avatar"));
                        attrs.putIfAbsent("authorities", userAttributes.get("authorities"));
                    }
                });

        if (state != null) {
            builder.attribute("state", state);
        }

        // Deserialize tokens
        if (tokens != null) {
            // Access token
            TokenWrapper accessTokenWrapper = tokens.get("access_token");
            if (accessTokenWrapper != null) {
                OAuth2AccessToken accessToken = accessTokenWrapper.toAccessToken();
                builder.accessToken(accessToken);
            }

            // Refresh token
            TokenWrapper refreshTokenWrapper = tokens.get("refresh_token");
            if (refreshTokenWrapper != null) {
                OAuth2RefreshToken refreshToken = refreshTokenWrapper.toRefreshToken();
                builder.refreshToken(refreshToken);
            }

            // Authorization code
            TokenWrapper codeWrapper = tokens.get("code");
            if (codeWrapper != null) {
                OAuth2AuthorizationCode code = codeWrapper.toAuthorizationCode();
                builder.token(code);
            }
        }

        return builder.build();
    }

    private Map<String, Object> extractUserAttributes(OAuth2Authorization authorization) {
        Map<String, Object> result = new HashMap<>();
        copyIfPresent(result, "user_id", authorization.getAttribute("user_id"));
        copyIfPresent(result, "tenant_id", authorization.getAttribute("tenant_id"));
        copyIfPresent(result, "dept_id", authorization.getAttribute("dept_id"));
        copyIfPresent(result, "username", authorization.getAttribute("username"));
        copyIfPresent(result, "real_name", authorization.getAttribute("real_name"));
        copyIfPresent(result, "phone", authorization.getAttribute("phone"));
        copyIfPresent(result, "avatar", authorization.getAttribute("avatar"));

        Object authoritiesObj = authorization.getAttribute("authorities");
        if (authoritiesObj instanceof Collection<?> collection) {
            result.put("authorities", collection.stream().map(Object::toString).toList());
        }
        return result;
    }

    private void copyIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    // Inner class for token serialization
    public static class TokenWrapper {

        @JsonProperty("tokenValue")
        private String tokenValue;

        @JsonProperty("issuedAt")
        private Instant issuedAt;

        @JsonProperty("expiresAt")
        private Instant expiresAt;

        @JsonProperty("tokenType")
        private String tokenType;

        @JsonProperty("scopes")
        private Set<String> scopes;

        @JsonProperty("metadata")
        private Map<String, Object> metadata;

        @JsonProperty("isInvalidated")
        private boolean isInvalidated;

        public TokenWrapper() {
        }

        public TokenWrapper(OAuth2Authorization.Token<?> token) {
            OAuth2Token oauth2Token = token.getToken();
            this.tokenValue = oauth2Token.getTokenValue();
            this.issuedAt = oauth2Token.getIssuedAt();
            this.expiresAt = oauth2Token.getExpiresAt();
            this.isInvalidated = token.isInvalidated();

            if (oauth2Token instanceof OAuth2AccessToken accessToken) {
                this.tokenType = accessToken.getTokenType().getValue();
                this.scopes = accessToken.getScopes();
            }

            // Copy metadata
            this.metadata = new HashMap<>(token.getMetadata());
        }

        public OAuth2AccessToken toAccessToken() {
            OAuth2AccessToken.TokenType tokenTypeEnum = OAuth2AccessToken.TokenType.BEARER;
            if (tokenType != null && "mac".equalsIgnoreCase(tokenType)) {
                tokenTypeEnum = OAuth2AccessToken.TokenType.BEARER;
            }
            return new OAuth2AccessToken(tokenTypeEnum, tokenValue, issuedAt, expiresAt, scopes);
        }

        public OAuth2RefreshToken toRefreshToken() {
            return new OAuth2RefreshToken(tokenValue, issuedAt, expiresAt);
        }

        public OAuth2AuthorizationCode toAuthorizationCode() {
            return new OAuth2AuthorizationCode(tokenValue, issuedAt, expiresAt);
        }
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRegisteredClientId() {
        return registeredClientId;
    }

    public void setRegisteredClientId(String registeredClientId) {
        this.registeredClientId = registeredClientId;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public void setPrincipalName(String principalName) {
        this.principalName = principalName;
    }

    public String getAuthorizationGrantType() {
        return authorizationGrantType;
    }

    public void setAuthorizationGrantType(String authorizationGrantType) {
        this.authorizationGrantType = authorizationGrantType;
    }

    public Set<String> getAuthorizedScopes() {
        return authorizedScopes;
    }

    public void setAuthorizedScopes(Set<String> authorizedScopes) {
        this.authorizedScopes = authorizedScopes;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Map<String, Object> getUserAttributes() {
        return userAttributes;
    }

    public void setUserAttributes(Map<String, Object> userAttributes) {
        this.userAttributes = userAttributes;
    }

    public Map<String, TokenWrapper> getTokens() {
        return tokens;
    }

    public void setTokens(Map<String, TokenWrapper> tokens) {
        this.tokens = tokens;
    }
}