package com.smart.nl2sql.infrastructure.llm;

/**
 * Minimal abstraction over a Chat LLM, so the NL2SQL engine does not depend
 * on a particular Spring AI configuration.
 *
 * <p>Implementations are expected to be thread-safe.
 */
public interface LlmClient {

    /**
     * Send a single-turn chat with explicit system + user prompts and return the assistant text.
     *
     * @param systemPrompt the system prompt; may be null
     * @param userPrompt   the user prompt; must not be null
     * @return the assistant response text, never null
     */
    String chat(String systemPrompt, String userPrompt);
}
