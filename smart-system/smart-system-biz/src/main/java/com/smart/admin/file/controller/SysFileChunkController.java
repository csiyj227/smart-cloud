package com.smart.admin.file.controller;

import com.smart.admin.file.entity.SysFile;
import com.smart.admin.file.entity.SysFileChunk;
import com.smart.admin.file.service.SysFileChunkService;
import com.smart.common.core.web.ApiResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传接口：init → uploadChunk × N → merge。
 *
 * <p>前端典型时序：
 * <pre>
 *  1. POST /file/chunk/init   { originalName, totalSize, chunkSize, totalChunks, fileMd5 }
 *     ← 返回 { uploadId, mergedFileId（秒传命中时不为空） }
 *  2. POST /file/chunk/upload?uploadId=&chunkNo= ＋ form-data file
 *     ← 返回 当前已上传分片号 list
 *  3. POST /file/chunk/merge  { uploadId }
 *     ← 返回 完整 SysFile
 * </pre>
 */
@RestController
@RequestMapping("/system/file/chunk")
@RequiredArgsConstructor
public class SysFileChunkController {

    private final SysFileChunkService chunkService;

    @PostMapping("/init")
    public ApiResult<SysFileChunk> init(@RequestBody InitRequest req) {
        return ApiResult.success(chunkService.init(req.getOriginalName(), req.getTotalSize(),
                req.getChunkSize(), req.getTotalChunks(), req.getFileMd5()));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<SysFileChunk> upload(@RequestParam String uploadId,
                                  @RequestParam Integer chunkNo,
                                  @RequestParam("file") MultipartFile chunk) {
        return ApiResult.success(chunkService.uploadChunk(uploadId, chunkNo, chunk));
    }

    @PostMapping("/merge")
    public ApiResult<SysFile> merge(@RequestBody MergeRequest req) {
        return ApiResult.success(chunkService.merge(req.getUploadId()));
    }

    @Data
    public static class InitRequest {
        private String originalName;
        private Long totalSize;
        private Integer chunkSize;
        private Integer totalChunks;
        private String fileMd5;
    }

    @Data
    public static class MergeRequest {
        private String uploadId;
    }
}
