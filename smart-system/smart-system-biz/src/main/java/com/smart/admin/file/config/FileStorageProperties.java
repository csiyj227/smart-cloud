package com.smart.admin.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置。
 *
 * <p>application.yml 示例：
 * <pre>
 * smart:
 *   file:
 *     storage-type: local      # local | minio
 *     local-base-dir: ${user.home}/smart-files
 *     download-base-url: /file/download
 *     minio:
 *       endpoint: http://localhost:9000
 *       access-key: minioadmin
 *       secret-key: minioadmin
 *       bucket-name: smart
 *       public-base-url: http://localhost:9000
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "smart.file")
public class FileStorageProperties {

    /** 存储类型：local（默认）或 minio */
    private String storageType = "local";

    /** 本地存储基础目录，默认 ${user.home}/smart-files */
    private String localBaseDir = System.getProperty("user.home") + "/smart-files";

    /** 文件下载接口前缀（无论本地/minio 都走此接口，由后端做权限校验） */
    private String downloadBaseUrl = "/system/file/download";

    /** 单文件最大大小（字节），默认 100 MB；分片上传不受此限制 */
    private long maxFileSize = 100L * 1024 * 1024;

    /** MinIO 配置 */
    private Minio minio = new Minio();

    @Data
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucketName = "smart";
        /** 对外可访问的 base URL，留空则统一走后端下载接口 */
        private String publicBaseUrl = "";
    }
}
