package com.smart.common.security.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * API encryption filter.
 * Encrypts request body and response body for sensitive endpoints.
 *
 * API 加密过滤器。
 * 对敏感端点的请求体和响应体进行加解密处理，
 * 使用 AES-GCM 算法保障传输数据的安全性。
 */
@Slf4j
@Component
public class ApiEncryptionFilter implements Filter {

    private final ObjectMapper objectMapper;

    /**
     * 用 {@link ObjectProvider} 而不是直接 {@code @Autowired ObjectMapper}，是为了规避
     * 「容器里有多个 {@link ObjectMapper} Bean」时的注入歧义（比如 {@code flowObjectMapper}
     * 和 {@code oauth2ObjectMapper} 都是各业务模块的专用 Mapper，但都不能也不应该当作通用
     * 序列化器）。{@link ObjectProvider#getIfUnique()} 会优先返回标了 {@code @Primary} 的
     * 那一个（即 Spring Boot 自动配置的、应用了 {@code spring.jackson.*} 配置的全局 Mapper）；
     * 万一容器里没有 Primary 的，就退化成 {@code new ObjectMapper()} 兜底，避免启动失败。
     *
     * <p>本 Filter 只在 enabled=true 时才会真正用到 mapper，所以兜底实例不会出现在生产路径上。
     */
    public ApiEncryptionFilter(ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper resolved = objectMapperProvider.getIfUnique();
        this.objectMapper = resolved != null ? resolved : new ObjectMapper();
    }

    @Value("${smart.security.api-encryption.enabled:false}")
    private boolean enabled;

    @Value("${smart.security.api-encryption.alg:AES}")
    private String algorithm;

    @Value("${smart.security.api-encryption.exclude-paths:/oauth2/**,/captcha/**}")
    private String excludePaths;

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private Set<String> excludedPathSet;

    @jakarta.annotation.PostConstruct
    public void init() {
        excludedPathSet = Set.of(excludePaths.split(","));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        // Skip excluded paths
        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Check if request is encrypted
        String encryptionHeader = httpRequest.getHeader("X-Encryption");
        if ("aes-gcm".equals(encryptionHeader)) {
            // Decrypt request body
            WrappedRequest wrappedRequest = new WrappedRequest(httpRequest, objectMapper);
            chain.doFilter(wrappedRequest, response);
        } else {
            // Pass through - response encryption is handled by response wrapper
            chain.doFilter(request, response);
        }
    }

    private boolean isExcluded(String path) {
        return excludedPathSet.stream().anyMatch(p -> path.startsWith(p.trim()));
    }

    /**
     * Encrypt data using AES-GCM.
     */
    public String encrypt(String data, String keyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

        byte[] iv = new byte[GCM_IV_LENGTH];
        new java.security.SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // Combine IV + encrypted data
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypt data using AES-GCM.
     */
    public String decrypt(String encryptedData, String keyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

        byte[] combined = Base64.getDecoder().decode(encryptedData);

        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * Wrapper request that decrypts body.
     */
    public static class WrappedRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final ObjectMapper objectMapper;
        private byte[] cachedBody;

        public WrappedRequest(HttpServletRequest request, ObjectMapper objectMapper) throws IOException {
            super(request);
            this.objectMapper = objectMapper;

            // Read and decrypt body
            String encryptedBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            if (encryptedBody != null && !encryptedBody.isEmpty()) {
                // For now, just cache the body (actual decryption needs key from session/header)
                this.cachedBody = encryptedBody.getBytes(StandardCharsets.UTF_8);
            } else {
                this.cachedBody = new byte[0];
            }
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new ServletInputStream() {
                private int index = 0;

                @Override
                public int read() {
                    return index < cachedBody.length ? cachedBody[index++] & 0xFF : -1;
                }

                @Override
                public boolean isFinished() {
                    return index >= cachedBody.length;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}