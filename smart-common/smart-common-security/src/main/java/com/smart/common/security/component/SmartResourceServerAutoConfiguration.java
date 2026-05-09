package com.smart.common.security.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Auto-configuration for OAuth2 resource server mode.
 * Activated via Spring Boot auto-configuration (spring.factories / AutoConfiguration.imports).
 *
 * OAuth2 资源服务器模式自动配置。
 * 通过 Spring Boot 自动装配机制激活，
 * 配置安全过滤链、JWT 转换器、密码编码器等核心安全组件。
 */
@Slf4j
@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class SmartResourceServerAutoConfiguration {

    @Bean
    public ServiceApiEndpointRegistry serviceApiEndpointRegistry(
            @org.springframework.context.annotation.Lazy RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return new ServiceApiEndpointRegistry(requestMappingHandlerMapping);
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http,
                                                         ServiceApiEndpointRegistry serviceApiEndpointRegistry) throws Exception {
        Set<String> serviceApiEndpoints = serviceApiEndpointRegistry.getServiceApiEndpoints();

        http.authorizeHttpRequests(auth -> {
                    for (String endpoint : serviceApiEndpoints) {
                        auth.requestMatchers(endpoint).permitAll();
                    }
                    auth.requestMatchers(
                            "/oauth2/token",
                            "/oauth2/introspect",
                            "/oauth2/revoke",
                            "/oauth2/jwks",
                            "/oauth2/authorize"
                    ).permitAll();
                    auth.requestMatchers(
                            "/actuator/**",
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/doc.html",
                            "/webjars/**",
                            "/system/tenant/list",
                            // 验证码接口：登录前获取验证码，必须匿名访问
                            "/auth/captcha/**",
                            // 社交登录：获取支持的提供商列表和授权 URL，登录前必须匿名访问；
                            // /auth/social/callback、/auth/social/bind、/auth/social/unbind 仍需认证
                            "/auth/social/providers",
                            "/auth/social/authorize/**",
                            // 文件下载/预览必须放行：<img>/<a> 标签发请求不会自动带 Authorization 头，
                            // 头像、富文本图片、附件预览等场景全靠这两个接口直链。
                            // 安全模型：文件 ID 是数据库自增数字，但 /system/file/page 列表接口仍鉴权，
                            // 攻击者无从枚举；如需更严，可在 download 内做 referer/签名校验。
                            "/system/file/download/**",
                            "/system/file/preview/**"
                    ).permitAll();
                    auth.anyRequest().authenticated();
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(this::convertJwtToAuthentication))
                );

        return http.build();
    }

    private AbstractAuthenticationToken convertJwtToAuthentication(Jwt jwt) {
        Collection<? extends GrantedAuthority> authorities = extractAuthorities(jwt.getClaim("authorities"));
        String principalName = resolvePrincipalName(jwt.getClaims(), jwt.getSubject());
        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }

    private Collection<? extends GrantedAuthority> extractAuthorities(Object rawAuthorities) {
        if (rawAuthorities instanceof Collection<?> collection) {
            return collection.stream()
                    .map(Object::toString)
                    .filter(value -> !value.isBlank())
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        }
        return java.util.List.of();
    }

    private String resolvePrincipalName(Map<String, Object> attributes, String defaultName) {
        Object username = firstNonNull(
                attributes.get("username"),
                attributes.get("user_name"),
                attributes.get("preferred_username"),
                attributes.get("sub")
        );
        return username != null ? String.valueOf(username) : defaultName;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
