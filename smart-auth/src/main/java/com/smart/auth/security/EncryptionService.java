package com.smart.auth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Encryption service for secure password transmission.
 * Supports RSA public key encryption and AES symmetric encryption.
 *
 * 加密服务，用于安全密码传输。
 * 支持 RSA 公钥加密和 AES 对称加密（GCM 模式）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptionService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RSA_KEY_PAIR_PREFIX = "auth:rsa_key_pair:";
    private static final int RSA_KEY_SIZE = 2048;
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${smart.security.encryption.rsa-public-key-cache-minutes:60}")
    private int rsaPublicKeyCacheMinutes;

    /**
     * Generate RSA key pair and return public key.
     * The private key is stored in Redis (server-side only).
     */
    public String generateRsaKeyPair(String sessionId) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(RSA_KEY_SIZE);
            KeyPair keyPair = keyGen.generateKeyPair();

            String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

            // Store private key in Redis (session-bound)
            redisTemplate.opsForValue().set(
                    RSA_KEY_PAIR_PREFIX + sessionId,
                    privateKey,
                    java.time.Duration.ofMinutes(rsaPublicKeyCacheMinutes)
            );

            log.debug("Generated RSA key pair for session: {}", sessionId);
            return publicKey;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    /**
     * Get RSA public key for a session.
     */
    public String getRsaPublicKey(String sessionId) {
        Object cached = redisTemplate.opsForValue().get(RSA_KEY_PAIR_PREFIX + sessionId);
        if (cached == null) {
            return generateRsaKeyPair(sessionId);
        }
        // Return a new public key (we need to regenerate since we only store private key)
        return generateRsaKeyPair(sessionId);
    }

    /**
     * Decrypt RSA encrypted data using session's private key.
     */
    public String decryptRsa(String sessionId, String encryptedData) {
        try {
            String privateKeyStr = (String) redisTemplate.opsForValue().get(RSA_KEY_PAIR_PREFIX + sessionId);
            if (privateKeyStr == null) {
                throw new IllegalStateException("RSA key pair not found for session: " + sessionId);
            }

            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyStr);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decrypted = cipher.doFinal(encryptedBytes);

            return new String(decrypted);
        } catch (Exception e) {
            log.error("RSA decryption failed for session: {}", sessionId, e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * Encrypt data using AES-GCM.
     * Returns: base64(iv + encrypted_data)
     */
    public String encryptAes(String plainText, String keyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] encrypted = cipher.doFinal(plainText.getBytes());

            // Combine IV + encrypted data
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("AES encryption failed", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt AES-GCM encrypted data.
     */
    public String decryptAes(String encryptedData, String keyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] combined = Base64.getDecoder().decode(encryptedData);

            // Extract IV and encrypted data
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted);
        } catch (Exception e) {
            log.error("AES decryption failed", e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * Generate a random AES key.
     */
    public String generateAesKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE);
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }

    /**
     * Clear RSA key pair for session (logout).
     */
    public void clearRsaKeyPair(String sessionId) {
        redisTemplate.delete(RSA_KEY_PAIR_PREFIX + sessionId);
        log.debug("Cleared RSA key pair for session: {}", sessionId);
    }
}