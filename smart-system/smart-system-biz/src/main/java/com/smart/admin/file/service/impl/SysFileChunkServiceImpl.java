package com.smart.admin.file.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.file.config.FileStorageProperties;
import com.smart.admin.file.entity.SysFile;
import com.smart.admin.file.entity.SysFileChunk;
import com.smart.admin.file.mapper.SysFileChunkMapper;
import com.smart.admin.file.service.SysFileChunkService;
import com.smart.admin.file.service.SysFileService;
import com.smart.admin.file.storage.FileStorage;
import com.smart.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 分片上传 Service 实现。
 *
 * <p>分片临时文件统一存放在 {@code ${localBaseDir}/.chunks/${uploadId}/${chunkNo}}，
 * 合并阶段读取所有分片按序写入主存储后清理临时目录。
 *
 * <p>这样设计避免 MinIO 等远端存储的 multipart upload API 与本地实现的差异，
 * 实现层只需关心"完整字节流"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileChunkServiceImpl extends ServiceImpl<SysFileChunkMapper, SysFileChunk>
        implements SysFileChunkService {

    private final FileStorage fileStorage;
    private final FileStorageProperties properties;
    private final SysFileService sysFileService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysFileChunk init(String originalName, Long totalSize, Integer chunkSize,
                             Integer totalChunks, String fileMd5) {
        if (originalName == null || totalSize == null || chunkSize == null || totalChunks == null) {
            throw new BusinessException("Missing required arguments");
        }

        // 秒传判定：先看 sys_file 里有没有同 md5 的物理文件可复用。
        // 这里不过滤 inRecycle：哪怕命中的源记录目前在回收站里，
        // 只要物理文件还在（fileStorage.exists 校验），就可以复用，避免重复落盘。
        if (fileMd5 != null && !fileMd5.isBlank()) {
            SysFile existing = sysFileService.getOne(new LambdaQueryWrapper<SysFile>()
                    .eq(SysFile::getMd5, fileMd5)
                    .last("LIMIT 1"), false);
            if (existing != null && fileStorage.exists(existing.getFilePath())) {
                SysFileChunk hit = new SysFileChunk();
                hit.setUploadId(IdUtil.fastSimpleUUID());
                hit.setOriginalName(originalName);
                hit.setTotalSize(totalSize);
                hit.setChunkSize(chunkSize);
                hit.setTotalChunks(totalChunks);
                hit.setFileMd5(fileMd5);
                hit.setStatus("merged");
                hit.setMergedFileId(existing.getId());
                hit.setUploadedChunks("");
                save(hit);
                log.info("Chunk init dedup hit, fileId={}", existing.getId());
                return hit;
            }
        }

        SysFileChunk chunk = new SysFileChunk();
        chunk.setUploadId(IdUtil.fastSimpleUUID());
        chunk.setOriginalName(originalName);
        chunk.setTotalSize(totalSize);
        chunk.setChunkSize(chunkSize);
        chunk.setTotalChunks(totalChunks);
        chunk.setFileMd5(fileMd5);
        chunk.setStatus("uploading");
        chunk.setUploadedChunks("");
        save(chunk);
        return chunk;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysFileChunk uploadChunk(String uploadId, Integer chunkNo, MultipartFile chunk) {
        SysFileChunk task = mustGet(uploadId);
        if ("merged".equalsIgnoreCase(task.getStatus())) {
            return task; // 已合并直接返回（秒传场景）
        }
        Path chunkFile = chunkDir(uploadId).resolve(String.valueOf(chunkNo));
        try {
            Files.createDirectories(chunkFile.getParent());
            try (InputStream in = chunk.getInputStream()) {
                Files.copy(in, chunkFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException("Failed to save chunk: " + e.getMessage());
        }

        // 已上传集合用 TreeSet 保证排序去重
        Set<Integer> uploaded = parseUploaded(task.getUploadedChunks());
        uploaded.add(chunkNo);
        String joined = uploaded.stream().map(String::valueOf).collect(Collectors.joining(","));
        update(new LambdaUpdateWrapper<SysFileChunk>()
                .eq(SysFileChunk::getUploadId, uploadId)
                .set(SysFileChunk::getUploadedChunks, joined));
        task.setUploadedChunks(joined);
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysFile merge(String uploadId) {
        SysFileChunk task = mustGet(uploadId);
        if ("merged".equalsIgnoreCase(task.getStatus()) && task.getMergedFileId() != null) {
            return sysFileService.getById(task.getMergedFileId());
        }

        Set<Integer> uploaded = parseUploaded(task.getUploadedChunks());
        if (uploaded.size() != task.getTotalChunks()) {
            throw new BusinessException("Missing chunks: expected " + task.getTotalChunks()
                    + ", got " + uploaded.size());
        }

        Path dir = chunkDir(uploadId);
        String ext = extractExt(task.getOriginalName());
        String storedName = IdUtil.fastSimpleUUID() + (ext.isEmpty() ? "" : "." + ext);
        String relativePath = LocalDate.now().format(DATE_FMT) + "/" + storedName;

        // 用管道把所有分片串成一个 InputStream 喂给 fileStorage（支持本地/MinIO 统一）
        String storedPath;
        try (PipedInputStream pis = new PipedInputStream(64 * 1024);
             PipedOutputStream pos = new PipedOutputStream(pis)) {

            Thread writer = new Thread(() -> {
                try (PipedOutputStream out = pos) {
                    for (int i = 1; i <= task.getTotalChunks(); i++) {
                        Path chunkFile = dir.resolve(String.valueOf(i));
                        try (InputStream cin = Files.newInputStream(chunkFile)) {
                            cin.transferTo(out);
                        }
                    }
                } catch (IOException e) {
                    log.error("Chunk merge writer failed", e);
                }
            }, "chunk-merge-" + uploadId);
            writer.setDaemon(true);
            writer.start();

            storedPath = fileStorage.store(pis, relativePath, task.getTotalSize(), null);
        } catch (IOException e) {
            throw new BusinessException("Failed to merge chunks: " + e.getMessage());
        }

        // 入库 sys_file
        SysFile entity = new SysFile();
        entity.setOriginalName(task.getOriginalName());
        entity.setStoredName(storedName);
        entity.setFilePath(storedPath);
        entity.setFileSize(task.getTotalSize());
        entity.setContentType("application/octet-stream");
        entity.setFileExt(ext);
        entity.setMd5(task.getFileMd5());
        entity.setStorageType(fileStorage.getType());
        entity.setBucketName("minio".equals(fileStorage.getType())
                ? properties.getMinio().getBucketName() : null);
        entity.setVersion(1);
        entity.setIsLatest(true);
        entity.setRefCount(1);
        entity.setInRecycle(false);
        // 先占位写 base url，等 save() 拿到自增 id 后再回写完整下载 URL
        // 与 SysFileServiceImpl.upload() 保持同样的两步走策略，避免前端拿到 "/file/download" 而 404
        entity.setFileUrl(properties.getDownloadBaseUrl());
        sysFileService.save(entity);
        // 拼 /file/download/{id} 并 update 回 DB
        String fullUrl = properties.getDownloadBaseUrl() + "/" + entity.getId();
        entity.setFileUrl(fullUrl);
        sysFileService.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysFile>()
                .eq(SysFile::getId, entity.getId())
                .set(SysFile::getFileUrl, fullUrl));

        // 更新分片任务状态 + 清理临时文件
        update(new LambdaUpdateWrapper<SysFileChunk>()
                .eq(SysFileChunk::getUploadId, uploadId)
                .set(SysFileChunk::getStatus, "merged")
                .set(SysFileChunk::getMergedFileId, entity.getId()));
        cleanupChunkDir(dir);
        return entity;
    }

    private SysFileChunk mustGet(String uploadId) {
        SysFileChunk task = getOne(new LambdaQueryWrapper<SysFileChunk>()
                .eq(SysFileChunk::getUploadId, uploadId)
                .last("LIMIT 1"), false);
        if (task == null) {
            throw new BusinessException("Upload task not found: " + uploadId);
        }
        return task;
    }

    private Path chunkDir(String uploadId) {
        return Paths.get(properties.getLocalBaseDir(), ".chunks", uploadId);
    }

    private static Set<Integer> parseUploaded(String s) {
        if (s == null || s.isBlank()) {
            return new TreeSet<>();
        }
        return Arrays.stream(s.split(","))
                .map(String::trim).filter(x -> !x.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String extractExt(String name) {
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return idx < 0 || idx == name.length() - 1 ? "" : name.substring(idx + 1).toLowerCase();
    }

    private static void cleanupChunkDir(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup chunk dir {}: {}", dir, e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private static Set<Integer> dedupOrdered(Set<Integer> in) {
        return new LinkedHashSet<>(in);
    }
}
