package com.smart.admin.file.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.file.entity.SysFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 文件管理 Service。覆盖：
 * <ul>
 *   <li>整文件上传（含 MD5 秒传 + 同名版本管理）</li>
 *   <li>下载（按 id，做权限/租户校验）</li>
 *   <li>列表分页 / 历史版本查看</li>
 *   <li>软删除（进回收站）/ 还原 / 彻底删除</li>
 *   <li>过期回收站清理（被定时任务调用）</li>
 * </ul>
 */
public interface SysFileService extends IService<SysFile> {

    /** 分页查询（默认只显示 isLatest=true 且 inRecycle=false 的最新版） */
    IPage<SysFile> pageLatest(Page<SysFile> page, String keyword);

    /** 分页查询回收站 */
    IPage<SysFile> pageRecycle(Page<SysFile> page, String keyword);

    /**
     * 上传单个文件（支持秒传 + 版本管理）。
     *
     * @return 入库后的 SysFile（含 id / 是否秒传命中）
     */
    SysFile upload(MultipartFile file);

    /** 下载：返回流 + 文件名（调用方负责设置响应头） */
    DownloadResult download(Long id);

    /** 列出某文件的所有历史版本（按 version 降序，含当前版） */
    List<SysFile> listVersions(Long id);

    /** 移入回收站 */
    void moveToRecycle(Long id);

    /** 从回收站还原 */
    void restore(Long id);

    /** 彻底删除（物理删除文件 + 数据库记录） */
    void purge(Long id);

    /** 清理回收站中超过 days 天的文件，返回清理数量（被定时任务调用） */
    int cleanRecycleExpired(int days);

    /** 下载结果包装，避免 Controller 重复处理 contentType / fileName */
    record DownloadResult(InputStream stream, String fileName, String contentType, long size) {}
}
