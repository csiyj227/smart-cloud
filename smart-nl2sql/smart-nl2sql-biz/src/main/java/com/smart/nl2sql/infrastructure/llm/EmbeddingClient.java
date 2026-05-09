package com.smart.nl2sql.infrastructure.llm;

import java.util.List;

/**
 * 向量化客户端抽象。
 *
 * <p>nl2sql 模块独立维护这套接口，避免直接耦合 smart-ai 的 EmbeddingModelFactory。
 * 实现类负责把文本调用嵌入模型转为向量；上层只关心一组 float[]，不感知模型细节。
 *
 * <p>实现需保证线程安全。
 */
public interface EmbeddingClient {

    /**
     * 单条文本 → 向量。
     * @param text 待向量化的文本，不可为 null/blank
     * @return float 数组，长度 = 模型维度（默认 1024，对齐 DashScope text-embedding-v3）
     */
    float[] embed(String text);

    /**
     * 批量文本 → 向量列表。实现可基于厂商批量 API 优化（一次 HTTP 多条），
     * 默认实现退化为多次单条调用。
     */
    default List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    /**
     * 把 float[] 序列化为 pgvector 字面量字符串（"[0.12,-0.34,...]"），
     * 用于直接拼到 SQL 的 {@code ?::vector} 占位符上。
     */
    static String toVectorLiteral(float[] vec) {
        if (vec == null || vec.length == 0) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        StringBuilder sb = new StringBuilder(vec.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
