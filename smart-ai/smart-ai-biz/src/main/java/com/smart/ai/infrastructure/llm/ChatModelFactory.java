package com.smart.ai.infrastructure.llm;

import com.smart.ai.infrastructure.persistence.entity.AiModelConfigEntity;
import com.smart.ai.infrastructure.persistence.entity.AiModelProviderEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiModelConfigMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiModelProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory that dynamically creates Spring AI {@link ChatModel} instances
 * based on provider and model configuration stored in the database.
 *
 * <p>All OpenAI-compatible providers (OpenAI, DeepSeek, DashScope, Ollama, Custom)
 * are handled through {@link OpenAiChatModel} with custom base URLs. This is the
 * standard approach in Spring AI 1.0 — most LLM providers expose an OpenAI-compatible
 * REST API, so a single client implementation covers them all.
 *
 * <p>ChatModel instances are cached by {@code modelConfigId} to avoid creating
 * new HTTP clients on every request. The cache is invalidated when configuration
 * changes (call {@link #evict(Long)}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatModelFactory {

    private final AiModelConfigMapper modelConfigMapper;
    private final AiModelProviderMapper modelProviderMapper;

    /** Cache: modelConfigId → ChatModel */
    private final Map<Long, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    /**
     * Get or create a ChatModel for the given model configuration ID.
     *
     * @param modelConfigId the model configuration ID from ai_model_config table
     * @return a ready-to-use ChatModel
     * @throws IllegalArgumentException if config or provider not found
     */
    public ChatModel getOrCreate(Long modelConfigId) {
        return chatModelCache.computeIfAbsent(modelConfigId, this::createChatModel);
    }

    /**
     * Build default chat options from model configuration.
     */
    public OpenAiChatOptions buildChatOptions(AiModelConfigEntity config) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(config.getModelCode());

        if (config.getMaxTokens() != null) {
            builder.maxTokens(config.getMaxTokens());
        }
        if (config.getTemperature() != null) {
            builder.temperature(config.getTemperature().doubleValue());
        }
        if (config.getTopP() != null) {
            builder.topP(config.getTopP().doubleValue());
        }

        return builder.build();
    }

    /**
     * Evict a cached ChatModel (call when provider/config is updated).
     */
    public void evict(Long modelConfigId) {
        chatModelCache.remove(modelConfigId);
    }

    /**
     * Clear all cached ChatModel instances.
     */
    public void evictAll() {
        chatModelCache.clear();
    }

    private ChatModel createChatModel(Long modelConfigId) {
        AiModelConfigEntity config = modelConfigMapper.selectById(modelConfigId);
        if (config == null) {
            throw new IllegalArgumentException("Model config not found: " + modelConfigId);
        }

        AiModelProviderEntity provider = modelProviderMapper.selectById(config.getProviderId());
        if (provider == null) {
            throw new IllegalArgumentException("Model provider not found: " + config.getProviderId());
        }

        String baseUrl = resolveBaseUrl(provider);
        String apiKey = provider.getApiKey();

        log.info("Creating ChatModel: provider={}, model={}, baseUrl={}",
                provider.getProviderName(), config.getModelCode(), baseUrl);

        // Spring AI 1.0 GA: OpenAiApi 和 OpenAiChatModel 的构造函数已 private，
        // 必须用 builder() 创建。
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        OpenAiChatOptions defaultOptions = buildChatOptions(config);

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(defaultOptions)
                .build();
    }

    /**
     * Resolve the base URL for a provider. Each provider type has a sensible default
     * that can be overridden by the user-configured baseUrl.
     */
    private String resolveBaseUrl(AiModelProviderEntity provider) {
        String configuredUrl = provider.getBaseUrl();
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl.endsWith("/") ? configuredUrl.substring(0, configuredUrl.length() - 1) : configuredUrl;
        }

        return switch (provider.getProviderType()) {
            case "OPENAI" -> "https://api.openai.com";
            case "DASHSCOPE" -> "https://dashscope.aliyuncs.com/compatible-mode";
            case "DEEPSEEK" -> "https://api.deepseek.com";
            case "OLLAMA" -> "http://localhost:11434";
            default -> throw new IllegalArgumentException(
                    "No base URL configured for provider: " + provider.getProviderName());
        };
    }
}
