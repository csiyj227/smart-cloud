package com.smart.admin.file.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.file.config.FileStorageProperties;
import com.smart.admin.file.entity.SysFile;
import com.smart.admin.file.mapper.SysFileMapper;
import com.smart.admin.file.service.SysFileService;
import com.smart.admin.file.storage.FileStorage;
import com.smart.common.core.exception.BusinessException;
import com.smart.common.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 文件管理核心 Service。
 *
 * <p>关键设计：
 * <ul>
 *   <li><b>秒传</b>：先算文件 MD5，如果 sys_file 里已经有同 md5 + 同 tenant 的记录，
 *       不再写物理文件，新建一条 SysFile 复用 storedName/filePath，refCount+1</li>
 *   <li><b>版本管理</b>：同 originalName + 同 tenant 已有最新版时，新建一条记录 version+1，
 *       parentId 指向版本组根，旧版 isLatest=false</li>
 *   <li><b>逻辑删</b>：moveToRecycle 仅置位 inRecycle=true + recycleTime=now，物理文件保留</li>
 *   <li><b>定时清理</b>：cleanRecycleExpired 扫描超过 days 天的回收站记录，
 *       refCount-- 至 0 时删物理文件，最后 remove DB 记录</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService {

    private final FileStorage fileStorage;
    private final FileStorageProperties properties;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    public IPage<SysFile> pageLatest(Page<SysFile> page, String keyword) {
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getIsLatest, true)
                .eq(SysFile::getInRecycle, false)
                .like(keyword != null && !keyword.isBlank(), SysFile::getOriginalName, keyword)
                .orderByDesc(SysFile::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public IPage<SysFile> pageRecycle(Page<SysFile> page, String keyword) {
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getInRecycle, true)
                .like(keyword != null && !keyword.isBlank(), SysFile::getOriginalName, keyword)
                .orderByDesc(SysFile::getRecycleTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysFile upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Empty file");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new BusinessException("File too large, please use chunked upload");
        }

        String md5;
        try (InputStream in = file.getInputStream()) {
            md5 = digestMd5(in);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BusinessException("Failed to read file: " + e.getMessage());
        }

        String originalName = file.getOriginalFilename();
        String contentType = file.getContentType();
        Long tenantId = TenantContext.get().orElse(null);

        // 1) 秒传命中：同 md5 + 同租户 已存在 → 复用 storedName/filePath，新建一条逻辑记录
        // 注意：必须按 tenantId 隔离，避免跨租户复用导致敏感文件越权下载
        SysFile dedup = findFirstByMd5SameTenant(md5, tenantId);
        if (dedup != null && fileStorage.exists(dedup.getFilePath())) {
            SysFile copy = newRecord(originalName, contentType, file.getSize(), md5,
                    dedup.getStoredName(), dedup.getFilePath());
            applyVersion(copy);
            save(copy);
            // 拿到自增 id 后回写完整下载 URL（同 newRecord 注释说明）
            fillDownloadUrlAndUpdate(copy);
            // 物理文件 refCount + 1（写到原始记录上，便于 cleanRecycleExpired 时判断）
            baseMapper.update(null, new LambdaUpdateWrapper<SysFile>()
                    .eq(SysFile::getId, dedup.getId())
                    .setSql("ref_count = ref_count + 1"));
            log.info("Dedup hit: original#{} (tenant={}) -> new#{}", dedup.getId(), tenantId, copy.getId());
            return copy;
        }

        // 2) 真正落物理文件
        String ext = extractExt(originalName);
        String storedName = IdUtil.fastSimpleUUID() + (ext.isEmpty() ? "" : "." + ext);
        String relativePath = LocalDate.now().format(DATE_FMT) + "/" + storedName;
        String storedPath;
        try (InputStream in = file.getInputStream()) {
            storedPath = fileStorage.store(in, relativePath, file.getSize(), contentType);
        } catch (IOException e) {
            throw new BusinessException("Failed to store file: " + e.getMessage());
        }

        SysFile entity = newRecord(originalName, contentType, file.getSize(), md5, storedName, storedPath);
        applyVersion(entity);
        save(entity);
        // 拿到自增 id 后回写完整下载 URL（同 newRecord 注释说明）
        fillDownloadUrlAndUpdate(entity);
        return entity;
    }

    @Override
    public DownloadResult download(Long id) {
        SysFile file = getById(id);
        if (file == null || Boolean.TRUE.equals(file.getInRecycle())) {
            throw new BusinessException("File not found or in recycle bin");
        }
        try {
            InputStream stream = fileStorage.load(file.getFilePath());
            return new DownloadResult(stream, file.getOriginalName(),
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                    file.getFileSize() != null ? file.getFileSize() : 0L);
        } catch (IOException e) {
            throw new BusinessException("Failed to load file: " + e.getMessage());
        }
    }

    @Override
    public List<SysFile> listVersions(Long id) {
        SysFile self = getById(id);
        if (self == null) {
            return List.of();
        }
        Long rootId = self.getParentId() != null ? self.getParentId() : self.getId();
        return list(new LambdaQueryWrapper<SysFile>()
                .and(w -> w.eq(SysFile::getId, rootId).or().eq(SysFile::getParentId, rootId))
                .orderByDesc(SysFile::getVersion));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveToRecycle(Long id) {
        SysFile file = getById(id);
        if (file == null) {
            return;
        }
        update(new LambdaUpdateWrapper<SysFile>()
                .eq(SysFile::getId, id)
                .set(SysFile::getInRecycle, true)
                .set(SysFile::getRecycleTime, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        update(new LambdaUpdateWrapper<SysFile>()
                .eq(SysFile::getId, id)
                .set(SysFile::getInRecycle, false)
                .set(SysFile::getRecycleTime, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purge(Long id) {
        SysFile file = getById(id);
        if (file == null) {
            return;
        }
        // 物理路径维度的引用计数：所有指向同一 filePath 的逻辑记录共同决定物理文件存活
        // 这里直接按 filePath 重新统计 siblings（除自身外），= 0 才真正删物理文件
        long siblings = count(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getFilePath, file.getFilePath())
                .ne(SysFile::getId, file.getId()));
        if (siblings == 0) {
            fileStorage.delete(file.getFilePath());
        } else {
            // 还有其他兄弟记录引用：把它们的 ref_count - 1 维持计数语义
            baseMapper.update(null, new LambdaUpdateWrapper<SysFile>()
                    .eq(SysFile::getFilePath, file.getFilePath())
                    .ne(SysFile::getId, file.getId())
                    .gt(SysFile::getRefCount, 0)
                    .setSql("ref_count = ref_count - 1"));
        }
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanRecycleExpired(int days) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        List<SysFile> expired = list(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getInRecycle, true)
                .lt(SysFile::getRecycleTime, threshold));
        int count = 0;
        for (SysFile f : expired) {
            try {
                purge(f.getId());
                count++;
            } catch (Exception e) {
                log.warn("Failed to purge expired file id={}: {}", f.getId(), e.getMessage());
            }
        }
        return count;
    }

    // ─────────── 内部辅助 ───────────

    /**
     * 查同租户同 md5 的最早一条（用作秒传源）。
     * <p>注意：必须显式按 tenantId 隔离，防止跨租户秒传命中导致越权访问其他租户文件。
     * 当 tenantId 为 null（如系统内部调用）时，仅匹配同样为 null 的记录。
     * <p>这里<b>不过滤 inRecycle</b>：哪怕命中的源文件目前在回收站里，
     * 只要物理文件还在（外层 fileStorage.exists 校验），就可以复用做秒传，避免重复落盘。
     */
    private SysFile findFirstByMd5SameTenant(String md5, Long tenantId) {
        LambdaQueryWrapper<SysFile> w = new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getMd5, md5);
        if (tenantId != null) {
            w.eq(SysFile::getTenantId, tenantId);
        } else {
            w.isNull(SysFile::getTenantId);
        }
        return getOne(w.orderByAsc(SysFile::getId).last("LIMIT 1"), false);
    }

    /**
     * 找到同 originalName 的"前一最新版"并维护版本链。
     * <p>关键：<b>不过滤 inRecycle</b>。否则回收站里同名旧记录会被忽略，导致：
     *   1) 新上传被当成 v1，与回收站里的旧记录"isLatest 都是 true 且同名"，产生脏数据；
     *   2) 用户在回收站看到一条同名记录，错以为"刚上传的文件居然进了回收站"。
     * 找到 prev 后无论它是否在回收站，都把它的 isLatest 置为 false，并把新记录接成 v+1。
     */
    private void applyVersion(SysFile fresh) {
        SysFile prevLatest = getOne(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getOriginalName, fresh.getOriginalName())
                .eq(SysFile::getIsLatest, true)
                .orderByDesc(SysFile::getVersion)
                .last("LIMIT 1"), false);
        if (prevLatest == null) {
            fresh.setVersion(1);
            fresh.setParentId(null);
            fresh.setIsLatest(true);
            return;
        }
        // 旧版本下台（不论是否在回收站）
        update(new LambdaUpdateWrapper<SysFile>()
                .eq(SysFile::getId, prevLatest.getId())
                .set(SysFile::getIsLatest, false));
        fresh.setVersion((prevLatest.getVersion() != null ? prevLatest.getVersion() : 1) + 1);
        fresh.setParentId(prevLatest.getParentId() != null ? prevLatest.getParentId() : prevLatest.getId());
        fresh.setIsLatest(true);
    }

    private SysFile newRecord(String originalName, String contentType, long size, String md5,
                              String storedName, String storedPath) {
        SysFile entity = new SysFile();
        entity.setOriginalName(originalName);
        entity.setStoredName(storedName);
        entity.setFilePath(storedPath);
        entity.setFileSize(size);
        entity.setContentType(contentType);
        entity.setFileExt(extractExt(originalName));
        entity.setMd5(md5);
        entity.setStorageType(fileStorage.getType());
        entity.setBucketName("minio".equals(fileStorage.getType())
                ? properties.getMinio().getBucketName() : null);
        entity.setRefCount(1);
        entity.setInRecycle(false);
        // 这里先占位写 base url，真正可下载的 URL 必须等 save() 拿到自增 id 后再回写。
        // 见 upload() / fillDownloadUrlAndUpdate() 的二次 update 调用。
        entity.setFileUrl(properties.getDownloadBaseUrl());
        return entity;
    }

    /**
     * 把 fileUrl 拼成最终可下载的 {@code /file/download/{id}} 形式并 update 回 DB。
     * 之所以分两步走（先 save 再 update），是因为 sys_file.id 是数据库自增主键，
     * 必须先 insert 拿到 id 才能拼出完整下载 URL。
     *
     * <p>带来的副作用：upload() 走完一共有 1 次 insert + 1 次 update，性能上比一次性 insert
     * 慢一点，但避免了把「拼 URL」逻辑泄漏到 controller 层（否则 controller、分页接口、
     * 文件管理列表、用户头像所有调用方都得各自拼一遍，DRY 全无）。
     *
     * <p>历史数据修复：旧记录的 file_url 字段都只有 {@code "/file/download"}，可执行：
     * <pre>UPDATE sys_file SET file_url = CONCAT('/file/download/', id) WHERE file_url = '/file/download';</pre>
     */
    private void fillDownloadUrlAndUpdate(SysFile entity) {
        String fullUrl = properties.getDownloadBaseUrl() + "/" + entity.getId();
        entity.setFileUrl(fullUrl);
        update(new LambdaUpdateWrapper<SysFile>()
                .eq(SysFile::getId, entity.getId())
                .set(SysFile::getFileUrl, fullUrl));
    }

    private static String extractExt(String name) {
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return idx < 0 || idx == name.length() - 1 ? "" : name.substring(idx + 1).toLowerCase();
    }

    private static String digestMd5(InputStream in) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            md.update(buf, 0, n);
        }
        return toHex(md.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
