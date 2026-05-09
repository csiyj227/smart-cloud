package com.smart.ai.infrastructure.llm;

import com.smart.ai.infrastructure.persistence.entity.AiModelConfigEntity;
import com.smart.ai.infrastructure.persistence.entity.AiModelProviderEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiModelConfigMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiModelProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory that dynamically creates Spring AI {@link EmbeddingModel} instances
 * for RAG vector embedding generation, based on database-driven model configuration.
 *
 * <p>Same design as {@link ChatModelFactory}: all OpenAI-compatible providers
 * (OpenAI / DashScope / DeepSeek / Ollama / Custom) go through {@link OpenAiEmbeddingModel}
 * with custom base URLs.
 *
 * <p>Recommended model codes:
 * <ul>
 *   <li>OpenAI: {@code text-embedding-3-small} (1536 dim) or {@code text-embedding-ada-002}</li>
 *   <li>DashScope (通义千问): {@code text-embedding-v1} or {@code text-embedding-v2} (1536 dim)</li>
 *   <li>Ollama: {@code nomic-embed-text} (768 dim, schema needs adjustment)</li>
 * </ul>
 *
 * <p>Important: the vector dimension in {@code ai_knowledge_segment.embedding} (default 1536)
 * must match the embedding model's output dimension. Mismatched models will fail at insert time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingModelFactory {

    private final AiModelConfigMapper modelConfigMapper;
    private final AiModelProviderMapper modelProviderMapper;

    /** Cache: modelConfigId → EmbeddingModel (HTTP clients are reused across requests). */
    private final Map<Long, EmbeddingModel> cache = new ConcurrentHashMap<>();

    /**
     * Get or create an EmbeddingModel for the given model configuration ID.
     *
     * @param modelConfigId id from ai_model_config table; should reference a model
     *                      whose model_code is an embedding model (e.g. text-embedding-3-small)
     * @return ready-to-use EmbeddingModel
     */
    public EmbeddingModel getOrCreate(Long modelConfigId) {
        return cache.computeIfAbsent(modelConfigId, this::create);
    }

    /** Evict cached EmbeddingModel after provider/config update. */
    public void evict(Long modelConfigId) {
        cache.remove(modelConfigId);
    }

    public void evictAll() {
        cache.clear();
    }

    private EmbeddingModel create(Long modelConfigId) {
        AiModelConfigEntity config = modelConfigMapper.selectById(modelConfigId);
        if (config == null) {
            throw new IllegalArgumentException("Embedding model config not found: " + modelConfigId);
        }
        AiModelProviderEntity provider = modelProviderMapper.selectById(config.getProviderId());
        if (provider == null) {
            throw new IllegalArgumentException("Embedding model provider not found: " + config.getProviderId());
        }

        String baseUrl = resolveBaseUrl(provider);
        log.info("Creating EmbeddingModel: provider={}, model={}, baseUrl={}",
                provider.getProviderName(), config.getModelCode(), baseUrl);

        // Spring AI 1.0 GA: OpenAiApi 必须用 builder() 创建。
        // OpenAiEmbeddingModel 的 4-arg 构造函数仍然是 public 的（embedding 模块尚未强制 builder），
        // 但为了和 ChatModelFactory 保持一致，统一用显式构造方式。
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(provider.getApiKey())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(config.getModelCode())
                .build();

        return new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
    }

    private String resolveBaseUrl(AiModelProviderEntity provider) {
        String configured = provider.getBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        }
        return switch (provider.getProviderType()) {
            case "OPENAI" -> "https://api.openai.com";
            case "DASHSCOPE", "QWEN" -> "https://dashscope.aliyuncs.com/compatible-mode";
            case "DEEPSEEK" -> "https://api.deepseek.com";
            case "OLLAMA" -> "http://localhost:11434";
            default -> throw new IllegalArgumentException(
                    "No base URL configured for embedding provider: " + provider.getProviderName());
        };
    }
}
