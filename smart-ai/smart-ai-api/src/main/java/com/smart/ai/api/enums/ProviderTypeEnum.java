package com.smart.ai.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI model provider types.
 */
@Getter
@RequiredArgsConstructor
public enum ProviderTypeEnum {

    OPENAI("OPENAI", "OpenAI"),
    AZURE("AZURE", "Azure OpenAI"),
    QWEN("QWEN", "Tongyi Qwen"),
    DEEPSEEK("DEEPSEEK", "DeepSeek"),
    OLLAMA("OLLAMA", "Ollama"),
    CUSTOM("CUSTOM", "Custom Provider");

    private final String code;
    private final String description;
}
