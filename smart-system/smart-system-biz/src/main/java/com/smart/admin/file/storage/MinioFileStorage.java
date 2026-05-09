package com.smart.admin.file.storage;

import com.smart.admin.file.config.FileStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * MinIO 对象存储实现。
 *
 * <p>仅当 {@code smart.file.storage-type=minio} 且 classpath 存在 {@link MinioClient} 时启用。
 * MinIO SDK 在 pom 中标记为 optional，因此即使不开启也不会污染其他存储路径。
 *
 * <p>对象 key 策略：直接使用业务传入的 relativePath（如 {@code 2026/05/02/uuid.png}），
 * 上层负责保证 key 的全局唯一性。
 */
@Slf4j
@Component
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "smart.file", name = "storage-type", havingValue = "minio")
public class MinioFileStorage implements FileStorage {

    private final FileStorageProperties properties;
    private final MinioClient client;

    public MinioFileStorage(FileStorageProperties properties) {
        this.properties = properties;
        this.client = MinioClient.builder()
                .endpoint(properties.getMinio().getEndpoint())
                .credentials(properties.getMinio().getAccessKey(), properties.getMinio().getSecretKey())
                .build();
    }

    @PostConstruct
    public void initBucket() {
        try {
            String bucket = properties.getMinio().getBucketName();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket created: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket init failed (will retry on first upload): {}", e.getMessage());
        }
    }

    @Override
    public String getType() {
        return "minio";
    }

    @Override
    public String store(InputStream input, String relativePath, long size, String contentType) throws IOException {
        try {
            // MinIO SDK PutObjectArgs.stream(in, objectSize, partSize) 规则：
            //   - objectSize 已知（>0）：partSize 必须传 -1，让 SDK 自己决定
            //   - objectSize 未知（=-1）：partSize 必须 ≥ 5 MiB，最多 5 GiB
            long objectSize = size > 0 ? size : -1L;
            long partSize = size > 0 ? -1L : 10L * 1024 * 1024;
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucketName())
                    .object(relativePath)
                    .stream(input, objectSize, partSize)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
            log.debug("MinIO stored: {} ({} bytes)", relativePath, size);
            return relativePath;
        } catch (Exception e) {
            throw new IOException("MinIO put object failed: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream load(String storedPath) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.getMinio().getBucketName())
                    .object(storedPath)
                    .build());
        } catch (Exception e) {
            throw new IOException("MinIO get object failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(String storedPath) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getMinio().getBucketName())
                    .object(storedPath)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("MinIO remove object failed {}: {}", storedPath, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean exists(String storedPath) {
        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(properties.getMinio().getBucketName())
                    .object(storedPath)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            log.warn("MinIO stat object failed {}: {}", storedPath, e.getMessage());
            return false;
        }
    }
}
