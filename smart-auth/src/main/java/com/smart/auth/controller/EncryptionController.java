package com.smart.auth.controller;

import com.smart.auth.security.EncryptionService;
import com.smart.common.core.web.ApiResult;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Encryption controller for secure password transmission.
 * Provides RSA public keys to frontend for client-side encryption.
 *
 * 加密控制器，用于安全密码传输。
 * 向前端提供 RSA 公钥，供客户端加密使用。
 */
@Slf4j
@RestController
@RequestMapping("/auth/encryption")
@RequiredArgsConstructor
public class EncryptionController {

    private final EncryptionService encryptionService;

    /**
     * Get RSA public key for current session.
     * The private key is stored server-side in Redis.
     *
     * GET /encryption/rsa-public-key?sessionId=xxx
     */
    @GetMapping("/rsa-public-key")
    public ApiResult<Map<String, String>> getRsaPublicKey(HttpSession session) {
        String sessionId = session.getId();
        String publicKey = encryptionService.getRsaPublicKey(sessionId);
        return ApiResult.success(Map.of("publicKey", publicKey));
    }

    /**
     * Generate new RSA key pair for current session.
     *
     * GET /encryption/rsa-key-pair
     */
    @GetMapping("/rsa-key-pair")
    public ApiResult<Map<String, String>> generateRsaKeyPair(HttpSession session) {
        String sessionId = session.getId();
        String publicKey = encryptionService.generateRsaKeyPair(sessionId);
        return ApiResult.success(Map.of("publicKey", publicKey));
    }

    /**
     * Clear RSA key pair (call on logout).
     *
     * POST /encryption/clear-rsa-key
     */
    @PostMapping("/clear-rsa-key")
    public ApiResult<Void> clearRsaKey(HttpSession session) {
        encryptionService.clearRsaKeyPair(session.getId());
        return ApiResult.success();
    }

    /**
     * Generate AES key for symmetric encryption.
     *
     * GET /encryption/aes-key
     */
    @GetMapping("/aes-key")
    public ApiResult<Map<String, String>> generateAesKey() {
        String aesKey = encryptionService.generateAesKey();
        return ApiResult.success(Map.of("aesKey", aesKey));
    }

    /**
     * Test endpoint to decrypt data (for debugging).
     *
     * POST /encryption/decrypt
     */
    @PostMapping("/decrypt")
    public ApiResult<String> decrypt(@RequestBody Map<String, String> params, HttpSession session) {
        String encryptedData = params.get("encryptedData");
        String sessionId = session.getId();

        if (encryptedData == null) {
            return ApiResult.failure("Missing encryptedData");
        }

        try {
            String decrypted = encryptionService.decryptRsa(sessionId, encryptedData);
            return ApiResult.success(decrypted);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            return ApiResult.failure("Decryption failed: " + e.getMessage());
        }
    }
}