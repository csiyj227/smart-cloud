package com.smart.admin.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分片上传任务记录。
 * 一个 uploadId 对应一次大文件分片上传过程，记录已上传分片号便于断点续传。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file_chunk")
public class SysFileChunk extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uploadId;
    private String originalName;
    private Long totalSize;
    private Integer chunkSize;
    private Integer totalChunks;
    /** 已上传分片序号，逗号分隔（"1,2,3,5"） */
    private String uploadedChunks;
    private String fileMd5;
    /** uploading / merged / failed */
    private String status;
    private Long mergedFileId;
    private Long tenantId;
}
