package com.smart.admin.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.file.entity.SysFile;
import com.smart.admin.file.service.SysFileService;
import com.smart.common.core.web.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件管理对外接口（整文件路径）。分片上传见 {@link SysFileChunkController}。
 */
@RestController
@RequestMapping("/system/file")
@RequiredArgsConstructor
public class SysFileController {

    private final SysFileService fileService;

    /** 分页：默认只列出最新版本且未在回收站 */
    @GetMapping("/page")
    public ApiResult<IPage<SysFile>> page(@RequestParam(defaultValue = "1") long current,
                                  @RequestParam(defaultValue = "10") long size,
                                  @RequestParam(required = false) String keyword) {
        return ApiResult.success(fileService.pageLatest(new Page<>(current, size), keyword));
    }

    /** 分页：回收站 */
    @GetMapping("/recycle/page")
    public ApiResult<IPage<SysFile>> recycle(@RequestParam(defaultValue = "1") long current,
                                     @RequestParam(defaultValue = "10") long size,
                                     @RequestParam(required = false) String keyword) {
        return ApiResult.success(fileService.pageRecycle(new Page<>(current, size), keyword));
    }

    /** 整文件上传（支持秒传 + 同名版本管理） */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<SysFile> upload(@RequestParam("file") MultipartFile file) {
        return ApiResult.success(fileService.upload(file));
    }

    /** 下载（按 id），支持中文文件名 */
    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SysFileService.DownloadResult res = fileService.download(id);
        String encoded = URLEncoder.encode(res.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        // 必须先设 Header，再写 OutputStream，否则响应已 commit、Header 会丢失
        // Content-Type 强制 application/octet-stream，避免浏览器对已知 MIME（text/html、image/* 等）做内联渲染
        response.reset();
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + sanitizeAscii(res.fileName()) + "\"; filename*=UTF-8''" + encoded);
        response.setContentType("application/octet-stream");
        if (res.size() > 0) {
            response.setContentLengthLong(res.size());
        }
        try (InputStream in = res.stream()) {
            in.transferTo(response.getOutputStream());
            response.flushBuffer();
        }
    }

    /** 将文件名中的非 ASCII 字符替换成 _，作为 RFC 6266 兼容头里的 fallback filename */
    private static String sanitizeAscii(String name) {
        if (name == null) return "download";
        StringBuilder sb = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            sb.append(c < 128 && c != '"' && c != '\\' ? c : '_');
        }
        return sb.toString();
    }

    /** 在线预览（图片/PDF 直接 inline 展示） */
    @GetMapping("/preview/{id}")
    public void preview(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SysFileService.DownloadResult res = fileService.download(id);
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''"
                + URLEncoder.encode(res.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setContentType(res.contentType());
        if (res.size() > 0) {
            response.setContentLengthLong(res.size());
        }
        try (InputStream in = res.stream()) {
            in.transferTo(response.getOutputStream());
            response.flushBuffer();
        }
    }

    /** 历史版本列表 */
    @GetMapping("/{id}/versions")
    public ApiResult<List<SysFile>> versions(@PathVariable Long id) {
        return ApiResult.success(fileService.listVersions(id));
    }

    /** 移入回收站 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> moveToRecycle(@PathVariable Long id) {
        fileService.moveToRecycle(id);
        return ApiResult.success();
    }

    /** 还原 */
    @PutMapping("/{id}/restore")
    public ApiResult<Void> restore(@PathVariable Long id) {
        fileService.restore(id);
        return ApiResult.success();
    }

    /** 彻底删除 */
    @DeleteMapping("/{id}/purge")
    public ApiResult<Void> purge(@PathVariable Long id) {
        fileService.purge(id);
        return ApiResult.success();
    }
}
