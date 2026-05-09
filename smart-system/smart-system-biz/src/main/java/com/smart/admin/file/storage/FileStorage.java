package com.smart.admin.file.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件存储抽象层。
 *
 * <p>所有具体存储后端（本地/MinIO/OSS/S3）都应实现此接口，
 * 上层 Service 通过依赖注入获得当前激活的实现，无需感知底层差异。
 *
 * <p>实现要点：
 * <ul>
 *   <li>{@link #store(InputStream, String, long, String)} 必须保证写入完整后才返回成功</li>
 *   <li>{@link #load(String)} 返回的 InputStream 由调用方负责 close</li>
 *   <li>{@link #delete(String)} 不存在的路径应返回 false 而非抛异常</li>
 * </ul>
 */
public interface FileStorage {

    /**
     * 当前实现的类型标识，会写入 {@code sys_file.storage_type}，便于读取时路由。
     */
    String getType();

    /**
     * 存储一个文件流到指定相对路径。
     *
     * @param input        文件流（调用方负责关闭）
     * @param relativePath 业务期望的相对路径（实现可基于此构造完整 key/path）
     * @param size         文件大小（字节），<=0 表示未知
     * @param contentType  MIME 类型，可为 null
     * @return 实际存储的物理路径或对象 key（写入 {@code sys_file.file_path}）
     */
    String store(InputStream input, String relativePath, long size, String contentType) throws IOException;

    /**
     * 读取一个已存储的文件。
     *
     * @param storedPath 来自 {@code sys_file.file_path}
     */
    InputStream load(String storedPath) throws IOException;

    /**
     * 删除一个已存储的文件。
     *
     * @return true 表示删除成功，false 表示文件不存在或删除失败
     */
    boolean delete(String storedPath);

    /**
     * 判断一个文件是否存在（用于秒传场景的物理文件存在性确认）。
     */
    boolean exists(String storedPath);
}
