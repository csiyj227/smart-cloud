package com.smart.admin.file.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.file.entity.SysFile;
import com.smart.admin.file.entity.SysFileChunk;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传 Service。
 *
 * <p>典型流程：前端调 {@link #init} 拿到 uploadId 与已上传分片号 → 多次 {@link #uploadChunk}
 * → 全部分片传完后调 {@link #merge} 触发后端合并并落 sys_file。
 */
public interface SysFileChunkService extends IService<SysFileChunk> {

    /** 初始化分片任务（同 md5 已存在则直接命中秒传，返回的 chunk.mergedFileId != null） */
    SysFileChunk init(String originalName, Long totalSize, Integer chunkSize, Integer totalChunks, String fileMd5);

    /** 上传单个分片（chunkNo 从 1 开始），返回更新后的进度记录 */
    SysFileChunk uploadChunk(String uploadId, Integer chunkNo, MultipartFile chunk);

    /** 合并所有分片为最终文件，返回入库的 SysFile */
    SysFile merge(String uploadId);
}
