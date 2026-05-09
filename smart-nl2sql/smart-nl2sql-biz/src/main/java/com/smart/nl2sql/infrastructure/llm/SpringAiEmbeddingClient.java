package com.smart.nl2sql.infrastructure.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认 {@link EmbeddingClient} 实现，基于 Spring AI 的 {@link EmbeddingModel}。
 *
 * <p>EmbeddingModel 由 spring-ai-openai-spring-boot-starter 自动装配，
 * 配置见 application.yml 的 spring.ai.openai.embedding.* 段。
 *
 * <p>用 ObjectProvider 而非直接注入 EmbeddingModel 是因为：
 * - 部分场景（如未配置 embedding 模型）应用应能正常启动，调用时再优雅报错；
 * - 避免与 smart-ai 模块的 EmbeddingModel Bean 产生冲突时启动失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiEmbeddingClient implements EmbeddingClient {

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("embed text must not be blank");
        }
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException(
                    "未配置 EmbeddingModel：请在 application.yml 的 spring.ai.openai.embedding 段配置 api-key/base-url/model");
        }
        try {
            return model.embed(text);
        } catch (Exception e) {
            log.error("Embedding 调用失败，text 长度={}", text.length(), e);
            throw new IllegalStateException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException(
                    "未配置 EmbeddingModel：请在 application.yml 的 spring.ai.openai.embedding 段配置 api-key/base-url/model");
        }
        try {
            EmbeddingResponse resp = model.embedForResponse(texts);
            // 顺序与 input 一致；逐条取出
            return resp.getResults().stream()
                    .map(r -> r.getOutput())
                    .toList();
        } catch (Exception e) {
            log.error("批量 Embedding 调用失败，size={}", texts.size(), e);
            throw new IllegalStateException("批量 Embedding 调用失败: " + e.getMessage(), e);
        }
    }
}
