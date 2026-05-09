package com.smart.nl2sql.infrastructure.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link LlmClient} implementation backed by Spring AI's {@link ChatModel}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiLlmClient implements LlmClient {

    private final ChatModel chatModel;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (userPrompt == null) {
            throw new IllegalArgumentException("userPrompt must not be null");
        }
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(userPrompt));
        try {
            return chatModel.call(new Prompt(messages))
                    .getResult()
                    .getOutput()
                    .getText();
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            throw new IllegalStateException("LLM 调用失败: " + e.getMessage(), e);
        }
    }
}
