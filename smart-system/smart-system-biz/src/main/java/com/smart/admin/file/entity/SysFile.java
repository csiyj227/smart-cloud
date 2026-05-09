package com.smart.admin.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件元数据。
 *
 * <p>存储策略说明：
 * <ul>
 *   <li>{@code storageType=local}：{@code filePath} 是本地绝对路径</li>
 *   <li>{@code storageType=minio}：{@code filePath} 是对象 key，需要拼接 {@code bucketName}</li>
 * </ul>
 *
 * <p>版本管理：同名文件再次上传不会覆盖物理文件，而是新建一条记录，{@code parentId} 指向第一版的 id，
 * 同时把旧版本的 {@code isLatest} 置为 false。
 *
 * <p>回收站：删除时把 {@code inRecycle=true}、{@code recycleTime=now}，定时任务扫描超过 30 天的彻底删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class SysFile extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String originalName;
    private String storedName;
    private String filePath;
    private String fileUrl;
    private Long fileSize;
    private String contentType;
    private String fileExt;
    private String md5;
    private String storageType;
    private String bucketName;
    private Integer version;
    private Long parentId;
    private Boolean isLatest;
    private Boolean inRecycle;
    private LocalDateTime recycleTime;
    private Integer refCount;
    private Long tenantId;
}
