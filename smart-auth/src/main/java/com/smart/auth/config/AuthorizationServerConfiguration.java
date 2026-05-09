package com.smart.auth.config;

import com.smart.auth.captcha.CaptchaService;
import com.smart.auth.security.PasswordRetryService;
import com.smart.auth.support.password.PasswordGrantAuthenticationConverter;
import com.smart.auth.support.password.PasswordGrantAuthenticationProvider;
import com.smart.auth.support.sms.SmsGrantAuthenticationConverter;
import com.smart.auth.support.sms.SmsGrantAuthenticationProvider;
import com.smart.common.data.tenant.TenantSwitchStore;
import com.smart.common.security.service.SmartUser;
import com.smart.common.security.service.SmartUserDetailsService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;

/**
 * OAuth2 Authorization Server configuration.
 * Sets up the authorization server filter chain with custom grant types (password, SMS).
 *
 * OAuth2 授权服务器配置。
 * 配置授权服务器过滤链，支持自定义授权类型（密码模式、短信验证码模式）。
 */
@Configuration
public class AuthorizationServerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationServerConfiguration.class);

    /**
     * RSA 密钥文件存放目录。默认为 ${user.home}/.smart/keys。
     * 可以通过配置 smart.auth.jwt.key-store-path 覆盖到容器持久化卷或共享存储。
     */
    @Value("${smart.auth.jwt.key-store-path:${user.home}/.smart/keys}")
    private String keyStorePath;

    /**
     * 固定的 keyId（避免每次重启变更）。
     */
    @Value("${smart.auth.jwt.key-id:smart-jwt-key}")
    private String keyId;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            PasswordGrantAuthenticationProvider passwordGrantAuthenticationProvider,
            SmsGrantAuthenticationProvider smsGrantAuthenticationProvider) throws Exception {
        // 使用 applyDefaultSecurity 让 Spring Authorization Server 注册默认行为，
        // 它会从容器查找 AuthorizationServerSettings bean 来配置端点路径。
        // 关键：authorizationServerSettings() bean 必须在此之前已注册到容器中。
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                        .accessTokenRequestConverter(new PasswordGrantAuthenticationConverter())
                        .accessTokenRequestConverter(new SmsGrantAuthenticationConverter())
                        .authenticationProvider(passwordGrantAuthenticationProvider)
                        .authenticationProvider(smsGrantAuthenticationProvider)
                );

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService() {
        return new org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService();
    }

    /**
     * 将 OAuth2 Client 元数据从内存切换为数据库读取（Smart 自定义的 {@code sys_oauth_client_details} 表）。
     *
     * <p>这样：
     * <ul>
     *   <li>新增 / 修改 client 不再需要重启应用</li>
     *   <li>多租户场景下可灵活管理 client（表里有 tenant_id）</li>
     *   <li>secret 在 DB 中以 BCrypt 形式存储，与登录接口的 PasswordEncoder 兼容</li>
     * </ul>
     *
     * <p>{@link SmartJdbcRegisteredClientRepository} 内置 ConcurrentHashMap 缓存，
     * 高并发登录场景下不会每次都查库。修改 client 后请通过该 Bean 的
     * {@code evictCache(clientId)} / {@code clearCache()} 失效缓存。
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new SmartJdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * JWK 源：RSA 密钥从磁盘加载；首次启动若不存在则生成并落盘，
     * 之后每次重启都复用同一对密钥，保证旧 JWT 仍可被验签（不再被迫重新登录）。
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = loadOrGenerateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * 从 {@link #keyStorePath} 目录加载 PKCS#8 / X.509 PEM 密钥；
     * 若目录中没有密钥文件，则现场生成一对并落盘（仅 owner 可读写）。
     */
    private KeyPair loadOrGenerateRsaKey() {
        try {
            Path dir = Paths.get(keyStorePath);
            Path privPath = dir.resolve("jwt-private.pem");
            Path pubPath = dir.resolve("jwt-public.pem");

            if (Files.exists(privPath) && Files.exists(pubPath)) {
                logger.info("Loading existing RSA key pair from {}", dir.toAbsolutePath());
                return loadKeyPair(privPath, pubPath);
            }

            logger.warn("RSA key pair not found at {}, generating a new one (subsequent restarts will reuse it)", dir.toAbsolutePath());
            Files.createDirectories(dir);
            KeyPair generated = generateRsaKey();
            saveKeyPair(generated, privPath, pubPath);
            return generated;
        } catch (Exception e) {
            // 兜底：磁盘不可用时降级为内存密钥（重启后旧 token 失效）
            logger.error("Failed to load/persist RSA key, falling back to in-memory key (tokens will not survive restart): {}", e.getMessage(), e);
            return generateRsaKey();
        }
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    private static KeyPair loadKeyPair(Path privPath, Path pubPath)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] privBytes = Base64.getDecoder().decode(stripPem(Files.readString(privPath)));
        byte[] pubBytes = Base64.getDecoder().decode(stripPem(Files.readString(pubPath)));

        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pubBytes));
        return new KeyPair(publicKey, privateKey);
    }

    private static void saveKeyPair(KeyPair keyPair, Path privPath, Path pubPath) throws IOException {
        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";

        Files.writeString(privPath, privatePem);
        Files.writeString(pubPath, publicPem);

        // POSIX 权限收紧：仅 owner 读写
        try {
            java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = java.util.EnumSet.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(privPath, perms);
        } catch (UnsupportedOperationException ignore) {
            // Windows 等非 POSIX 文件系统忽略
        }
        logger.info("Persisted RSA key pair to {}", privPath.getParent().toAbsolutePath());
    }

    private static String stripPem(String pem) {
        return pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * JWT 签发钩子。两个职责：
     * <ol>
     *   <li>把 {@link SmartUser} 的扩展字段（user_id / tenant_id / dept_id 等）写进 JWT claims</li>
     *   <li><b>清理该用户在 Redis 中的"切换租户 override"</b>—— 重新登录或刷新 token
     *       意味着用户进入新会话，残留的旧 override（比如上次切到了租户 0）必须失效，
     *       否则会出现"登录传 tenant_id=1，业务查询却查租户 0"的诡异现象。</li>
     * </ol>
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(TenantSwitchStore tenantSwitchStore) {
        return context -> {
            if (context.getTokenType() == null || !"access_token".equals(context.getTokenType().getValue())) {
                return;
            }

            Object principal = context.getPrincipal() != null ? context.getPrincipal().getPrincipal() : null;
            if (!(principal instanceof SmartUser smartUser)) {
                return;
            }

            // 写入 JWT 扩展 claims
            context.getClaims().claim("user_id", smartUser.getUserId());
            context.getClaims().claim("tenant_id", smartUser.getTenantId());
            context.getClaims().claim("dept_id", smartUser.getDeptId());
            context.getClaims().claim("username", smartUser.getUsername());
            context.getClaims().claim("real_name", smartUser.getRealName());
            context.getClaims().claim("phone", smartUser.getPhone());
            context.getClaims().claim("avatar", smartUser.getAvatar());
            context.getClaims().claim("authorities", smartUser.getAuthorities().stream()
                    .map(grantedAuthority -> grantedAuthority.getAuthority())
                    .toList());

            // 清理上一次会话残留的"切换租户 override"，让用户回到 JWT 中的默认租户
            // 这样可以防止用户上次切换到 tenant=0 后退出登录，重新登录时仍被旧 override 影响
            try {
                if (smartUser.getUserId() != null) {
                    tenantSwitchStore.clearOverride(smartUser.getUserId());
                    logger.info("Cleared tenant override on token issuance for user {} (tenant_id={})",
                            smartUser.getUserId(), smartUser.getTenantId());
                }
            } catch (Exception e) {
                // 不能因为清缓存失败影响登录主流程
                logger.warn("Failed to clear tenant override for user {}: {}", smartUser.getUserId(), e.getMessage());
            }
        };
    }

    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer) {
        NimbusJwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(jwtTokenCustomizer);
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, accessTokenGenerator, refreshTokenGenerator);
    }

    @Bean
    public PasswordGrantAuthenticationProvider passwordGrantAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            SmartUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            PasswordRetryService passwordRetryService,
            CaptchaService captchaService,
            ApplicationEventPublisher eventPublisher) {
        return new PasswordGrantAuthenticationProvider(
                authorizationService, tokenGenerator, userDetailsService, passwordEncoder,
                passwordRetryService, captchaService, eventPublisher);
    }

    @Bean
    public SmsGrantAuthenticationProvider smsGrantAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            SmartUserDetailsService userDetailsService) {
        return new SmsGrantAuthenticationProvider(
                authorizationService, tokenGenerator, userDetailsService);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }
}