package com.smart.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Smart 平台自有的 {@link RegisteredClientRepository} JDBC 实现。
 *
 * <p>本仓库使用 Smart 自定义的 OAuth2 客户端表 {@code sys_oauth_client_details}。
 * 表结构选用业内通行的 OAuth2 客户端字段集合：
 * <pre>
 *   client_id          客户端 ID
 *   client_secret      客户端密钥（项目约定使用 BCrypt 编码）
 *   scope              授权范围，多值以英文逗号分隔
 *   authorized_grant_types  允许的 grant 类型，多值以英文逗号分隔
 *   web_server_redirect_uri 授权码 / SSO 回调地址，多值以英文逗号分隔
 *   access_token_validity   access token 有效期（秒）
 *   refresh_token_validity  refresh token 有效期（秒）
 *   auto_approve            是否自动通过授权同意
 *   tenant_id               所属租户
 * </pre>
 *
 * <p>设计要点：
 * <ul>
 *   <li>字段语义自定义：grant 类型 / scope / 回调 URI 全部走文本字段拆分，
 *       便于通过后台管理页直接维护，不引入 JSON / 数组类型，兼容性更好。</li>
 *   <li>不直接依赖 Spring 官方 {@code JdbcRegisteredClientRepository}，原因是
 *       官方实现绑死了 13 列固定 DDL（包含 client_settings、token_settings 两个 JSON 字段），
 *       与本项目通用、可读的字段方案不符，因此自行实现一个轻量映射层。</li>
 *   <li>查询结果使用 {@link ConcurrentHashMap} 缓存，避免高并发登录场景每次都查库。
 *       管理后台变更 client 后需要主动调用 {@link #evictCache(String)} 或 {@link #clearCache()}。</li>
 * </ul>
 */
public class SmartJdbcRegisteredClientRepository implements RegisteredClientRepository {

    private static final Logger log = LoggerFactory.getLogger(SmartJdbcRegisteredClientRepository.class);

    private static final String SELECT_BASE = """
            SELECT client_id, client_secret, scope, authorized_grant_types,
                   web_server_redirect_uri, access_token_validity, refresh_token_validity,
                   auto_approve, additional_information
            FROM sys_oauth_client_details
            """;

    private static final String FIND_BY_CLIENT_ID = SELECT_BASE + " WHERE client_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<RegisteredClient> rowMapper = new RegisteredClientRowMapper();
    private final Map<String, RegisteredClient> cache = new ConcurrentHashMap<>(16);

    public SmartJdbcRegisteredClientRepository(JdbcTemplate jdbcTemplate) {
        Assert.notNull(jdbcTemplate, "jdbcTemplate cannot be null");
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 当前未提供持久化保存能力（OAuth Client 的增删改由后台管理页 + Mapper 完成）。
     * 这里仅用于支持调用方在内存中刷新缓存。
     */
    @Override
    public void save(RegisteredClient registeredClient) {
        Assert.notNull(registeredClient, "registeredClient cannot be null");
        log.debug("save() called for clientId={}, only refreshing cache (DB write should go through SysOauthClient management API)",
                registeredClient.getClientId());
        cache.put(registeredClient.getClientId(), registeredClient);
    }

    @Override
    @Nullable
    public RegisteredClient findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        // 当前实现里 RegisteredClient.id 与 client_id 同值（见 RowMapper）
        return findByClientId(id);
    }

    @Override
    @Nullable
    public RegisteredClient findByClientId(String clientId) {
        Assert.hasText(clientId, "clientId cannot be empty");
        RegisteredClient cached = cache.get(clientId);
        if (cached != null) {
            return cached;
        }
        try {
            RegisteredClient client = jdbcTemplate.queryForObject(FIND_BY_CLIENT_ID, rowMapper, clientId);
            if (client != null) {
                cache.put(clientId, client);
            }
            return client;
        } catch (EmptyResultDataAccessException e) {
            log.warn("RegisteredClient not found for clientId={}", clientId);
            return null;
        }
    }

    /**
     * 失效单个 client 的缓存（建议在管理后台保存 / 删除 client 后调用）。
     */
    public void evictCache(String clientId) {
        if (clientId != null) {
            cache.remove(clientId);
        }
    }

    /**
     * 清空全部缓存。
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * 把表行转换为 {@link RegisteredClient}。
     */
    private static class RegisteredClientRowMapper implements RowMapper<RegisteredClient> {

        @Override
        public RegisteredClient mapRow(ResultSet rs, int rowNum) throws SQLException {
            String clientId = rs.getString("client_id");
            String clientSecret = rs.getString("client_secret");
            String scopeStr = rs.getString("scope");
            String grantTypeStr = rs.getString("authorized_grant_types");
            String redirectUriStr = rs.getString("web_server_redirect_uri");
            int accessTokenValidity = rs.getInt("access_token_validity");
            int refreshTokenValidity = rs.getInt("refresh_token_validity");
            String autoApprove = rs.getString("auto_approve");

            RegisteredClient.Builder builder = RegisteredClient.withId(clientId)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .clientName(clientId);

            // scopes
            if (StringUtils.hasText(scopeStr)) {
                for (String scope : scopeStr.split(",")) {
                    String trimmed = scope.trim();
                    if (!trimmed.isEmpty()) {
                        builder.scope(trimmed);
                    }
                }
            } else {
                builder.scope("server");
            }

            // grant types
            boolean hasAuthorizationCode = false;
            if (StringUtils.hasText(grantTypeStr)) {
                for (String grant : grantTypeStr.split(",")) {
                    AuthorizationGrantType type = resolveGrantType(grant.trim());
                    if (type != null) {
                        builder.authorizationGrantType(type);
                        if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(type)) {
                            hasAuthorizationCode = true;
                        }
                    }
                }
            }

            // redirect URIs（authorization_code / sso 场景才会用到）
            boolean hasRedirectUri = false;
            if (StringUtils.hasText(redirectUriStr)) {
                for (String uri : redirectUriStr.split(",")) {
                    String trimmed = uri.trim();
                    if (!trimmed.isEmpty()) {
                        builder.redirectUri(trimmed);
                        hasRedirectUri = true;
                    }
                }
            }

            // Spring Authorization Server 硬性要求：一旦启用 authorization_code，
            // RegisteredClient 必须至少有一个 redirectUri，否则 build() 直接抛
            // IllegalArgumentException("redirectUris cannot be empty")。
            // 这里给一个本地占位 URI 作为兜底，避免后台为 password / client_credentials
            // 场景的 client 配置了 authorization_code 但忘填 redirect_uri 时整个登录链路 401。
            // 如果真的要走 authorization_code 流程，请在管理后台为该 client 配置真实的 redirect_uri。
            if (hasAuthorizationCode && !hasRedirectUri) {
                builder.redirectUri("http://localhost");
            }

            // token / client settings
            TokenSettings.Builder tokenSettings = TokenSettings.builder();
            if (accessTokenValidity > 0) {
                tokenSettings.accessTokenTimeToLive(Duration.ofSeconds(accessTokenValidity));
            } else {
                tokenSettings.accessTokenTimeToLive(Duration.ofHours(12));
            }
            if (refreshTokenValidity > 0) {
                tokenSettings.refreshTokenTimeToLive(Duration.ofSeconds(refreshTokenValidity));
            } else {
                tokenSettings.refreshTokenTimeToLive(Duration.ofDays(30));
            }
            builder.tokenSettings(tokenSettings.build());

            boolean autoApproveAll = "true".equalsIgnoreCase(autoApprove);
            builder.clientSettings(ClientSettings.builder()
                    .requireAuthorizationConsent(!autoApproveAll)
                    .build());

            return builder.build();
        }

        private static final Map<String, AuthorizationGrantType> KNOWN_GRANTS = new HashMap<>();
        static {
            KNOWN_GRANTS.put("password", AuthorizationGrantType.PASSWORD);
            KNOWN_GRANTS.put("refresh_token", AuthorizationGrantType.REFRESH_TOKEN);
            KNOWN_GRANTS.put("authorization_code", AuthorizationGrantType.AUTHORIZATION_CODE);
            KNOWN_GRANTS.put("client_credentials", AuthorizationGrantType.CLIENT_CREDENTIALS);
            KNOWN_GRANTS.put("device_code", AuthorizationGrantType.DEVICE_CODE);
            KNOWN_GRANTS.put("urn:ietf:params:oauth:grant-type:jwt-bearer", AuthorizationGrantType.JWT_BEARER);
        }

        private AuthorizationGrantType resolveGrantType(String grant) {
            if (grant == null || grant.isEmpty()) {
                return null;
            }
            AuthorizationGrantType known = KNOWN_GRANTS.get(grant);
            if (known != null) {
                return known;
            }
            // 自定义 grant 类型（如 sms、social 等）
            return new AuthorizationGrantType(grant);
        }
    }

}
