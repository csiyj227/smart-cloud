package com.smart.ai.interfaces.rest;

import com.smart.common.core.web.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * File upload endpoint for AI module (knowledge base documents, agent avatars, multimodal images).
 *
 * <p>Files are stored on the local filesystem under {@code ai.upload.base-dir}, organized by
 * date (yyyy/MM/dd). For production, consider switching to OSS/Minio/S3 by replacing
 * the storage logic in {@link #upload(MultipartFile, String)}.
 */
@Slf4j
@RestController
@RequestMapping("/ai/file")
@Tag(name = "AI File Upload")
public class FileUploadController {

    @Value("${ai.upload.base-dir:./ai-upload}")
    private String baseDir;

    @Value("${ai.upload.access-prefix:/ai/file/download}")
    private String accessPrefix;

    @Value("${ai.upload.max-size-mb:50}")
    private long maxSizeMb;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @PostConstruct
    public void init() {
        try {
            Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
            Files.createDirectories(basePath);
            log.info("AI upload base directory initialized: {}", basePath);
        } catch (IOException e) {
            log.error("Failed to initialize upload directory {}: {}", baseDir, e.getMessage(), e);
        }
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload a file (knowledge document, image, avatar)")
    @PreAuthorize("@authz.hasPermission('ai_chat')")
    public ApiResult<UploadResult> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "category", defaultValue = "default") String category) {
        if (file == null || file.isEmpty()) {
            return ApiResult.failure("File is empty");
        }

        long sizeMb = file.getSize() / 1024 / 1024;
        if (sizeMb > maxSizeMb) {
            return ApiResult.failure("File too large. Max allowed: " + maxSizeMb + "MB, actual: " + sizeMb + "MB");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "unnamed";
        }

        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex);
        }

        String dateDir = LocalDate.now().format(DATE_FMT);
        String safeCategory = category.replaceAll("[^a-zA-Z0-9_-]", "_");
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        String relativePath = String.format("%s/%s/%s", safeCategory, dateDir, fileName);

        try {
            Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
            Path targetPath = basePath.resolve(relativePath).normalize();

            // Security: ensure target is within base dir
            if (!targetPath.startsWith(basePath)) {
                return ApiResult.failure("Invalid file path");
            }

            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);

            UploadResult result = new UploadResult();
            result.setOriginalName(originalName);
            result.setFileName(fileName);
            result.setRelativePath(relativePath);
            result.setUrl(accessPrefix + "/" + relativePath);
            result.setSize(file.getSize());
            result.setContentType(file.getContentType());

            log.info("File uploaded: {} -> {}", originalName, relativePath);
            return ApiResult.success(result);
        } catch (IOException e) {
            log.error("File upload failed: {}", e.getMessage(), e);
            return ApiResult.failure("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/download/{category}/{year}/{month}/{day}/{fileName}")
    @Operation(summary = "Download a previously uploaded file")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable String category,
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable String day,
            @PathVariable String fileName) {
        try {
            Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
            Path filePath = basePath.resolve(String.format("%s/%s/%s/%s/%s",
                    category, year, month, day, fileName)).normalize();

            if (!filePath.startsWith(basePath) || !Files.exists(filePath)) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource =
                    new org.springframework.core.io.UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return org.springframework.http.ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (IOException e) {
            log.error("File download failed: {}", e.getMessage(), e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    @Data
    public static class UploadResult implements Serializable {
        private String originalName;
        private String fileName;
        private String relativePath;
        private String url;
        private Long size;
        private String contentType;
    }
}
