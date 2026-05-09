package com.smart.auth.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * GitHub social login provider.
 *
 * GitHub 社交登录提供者，通过 GitHub OAuth2 实现第三方登录。
 */
@Slf4j
@Component
public class GitHubLoginProvider implements SocialLoginProvider {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    /**
     * 同 {@code ApiEncryptionFilter}：容器里有多个 {@link ObjectMapper} Bean
     * （flowObjectMapper / oauth2ObjectMapper / 自动配置的 jacksonObjectMapper），
     * 用 {@link ObjectProvider#getIfUnique()} 优先取标了 {@code @Primary} 的全局 Mapper，
     * 避免「构造器注入歧义」导致启动失败。
     */
    public GitHubLoginProvider(ObjectProvider<ObjectMapper> objectMapperProvider,
                               RestTemplate restTemplate) {
        ObjectMapper resolved = objectMapperProvider.getIfUnique();
        this.objectMapper = resolved != null ? resolved : new ObjectMapper();
        this.restTemplate = restTemplate;
    }

    @Value("${smart.social.github.client-id:}")
    private String clientId;

    @Value("${smart.social.github.client-secret:}")
    private String clientSecret;

    @Value("${smart.social.github.redirect-uri:}")
    private String redirectUri;

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_INFO_URL = "https://api.github.com/user";

    @Override
    public String getProviderType() {
        return "github";
    }

    @Override
    public String getAuthorizationUrl(String redirectUri, String state) {
        String effectiveRedirectUri = redirectUri != null ? redirectUri : this.redirectUri;
        return AUTHORIZE_URL + "?client_id=" + clientId
                + "&redirect_uri=" + effectiveRedirectUri
                + "&scope=read:user user:email"
                + "&state=" + state;
    }

    @Override
    public SocialLoginService.SocialUserInfo getUserInfo(String code) {
        try {
            // Exchange code for access token
            Map<String, String> tokenParams = new HashMap<>();
            tokenParams.put("client_id", clientId);
            tokenParams.put("client_secret", clientSecret);
            tokenParams.put("code", code);

            String tokenResponse = restTemplate.postForObject(TOKEN_URL, tokenParams, String.class);
            JsonNode tokenNode = objectMapper.readTree(tokenResponse);
            String accessToken = tokenNode.get("access_token").asText();

            // Get user info
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            headers.put("Accept", "application/json");

            String userResponse = restTemplate.getForObject(USER_INFO_URL + "?access_token=" + accessToken, String.class);
            JsonNode userNode = objectMapper.readTree(userResponse);

            String openId = userNode.get("id").asText();
            String nickname = userNode.get("login").asText();
            String avatar = userNode.has("avatar_url") ? userNode.get("avatar_url").asText() : null;
            String email = userNode.has("email") && !userNode.get("email").isNull()
                    ? userNode.get("email").asText() : null;
            String gender = "unknown";

            // Try to get email if not public
            if (email == null) {
                String emailsResponse = restTemplate.getForObject(
                        "https://api.github.com/user/emails?access_token=" + accessToken, String.class);
                JsonNode emailsNode = objectMapper.readTree(emailsResponse);
                if (emailsNode.isArray() && emailsNode.size() > 0) {
                    email = emailsNode.get(0).get("email").asText();
                }
            }

            log.info("GitHub login: openId={}, nickname={}", openId, nickname);
            return new SocialLoginService.SocialUserInfo(openId, nickname, avatar, email, gender, "github");
        } catch (Exception e) {
            log.error("Failed to get GitHub user info", e);
            throw new RuntimeException("Failed to authenticate with GitHub", e);
        }
    }
}