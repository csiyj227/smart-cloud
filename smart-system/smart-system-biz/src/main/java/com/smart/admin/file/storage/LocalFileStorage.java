package com.smart.admin.file.storage;

import com.smart.admin.file.config.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储实现。
 *
 * <p>路径策略：{@code ${localBaseDir}/${relativePath}}，会自动创建中间目录。
 *
 * <p>仅当 {@code smart.file.storage-type=local}（默认）时启用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "smart.file", name = "storage-type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final FileStorageProperties properties;

    @Override
    public String getType() {
        return "local";
    }

    @Override
    public String store(InputStream input, String relativePath, long size, String contentType) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Local stored: {} ({} bytes)", target, size);
        return target.toAbsolutePath().toString();
    }

    @Override
    public InputStream load(String storedPath) throws IOException {
        return Files.newInputStream(Paths.get(storedPath));
    }

    @Override
    public boolean delete(String storedPath) {
        try {
            return Files.deleteIfExists(Paths.get(storedPath));
        } catch (IOException e) {
            log.warn("Failed to delete local file {}: {}", storedPath, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean exists(String storedPath) {
        return storedPath != null && Files.exists(Paths.get(storedPath));
    }

    private Path resolve(String relativePath) {
        // 防止 ../ 路径穿越
        String safe = relativePath.replace("..", "_");
        return Paths.get(properties.getLocalBaseDir(), safe).normalize();
    }
}
