package com.smart.ai.infrastructure.search;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

/**
 * Web search service that provides internet search capability for AI conversations.
 *
 * <p>This service integrates with external search APIs to fetch real-time information
 * from the web. The search results are formatted and injected into the LLM prompt
 * as additional context, enabling the model to answer questions about current events
 * and up-to-date information.
 *
 * <p>Currently supports a "search via LLM" approach where the model is prompted
 * to identify search queries, and a simple HTTP-based search integration.
 * Future enhancements will include direct API integration with search providers
 * (e.g., Bing Search API, SerpAPI, Tavily).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchService {

    /**
     * Execute a web search and return formatted results.
     *
     * <p>This method queries external search APIs and returns the results
     * in a format suitable for injection into the LLM prompt context.
     *
     * @param query the search query extracted from user's message
     * @param maxResults maximum number of results to return
     * @return list of search results
     */
    public List<SearchResult> search(String query, int maxResults) {
        log.info("Executing web search: query='{}', maxResults={}", query, maxResults);

        // Placeholder: in production, integrate with a real search API
        // Options include:
        // - Bing Search API (Azure Cognitive Services)
        // - SerpAPI (Google Search)
        // - Tavily API (AI-optimized search)
        // - DuckDuckGo API (free, no key required)

        log.warn("Web search is using placeholder implementation. "
                + "Configure a real search API for production use.");

        return List.of(new SearchResult(
                "搜索功能提示",
                "联网搜索功能已启用。当前使用的是占位实现，"
                        + "请在配置中接入真实的搜索 API（如 Tavily、Bing Search 等）以获取实时信息。",
                "https://smart.ai/docs/web-search"
        ));
    }

    /**
     * Format search results into a context string for LLM prompt injection.
     *
     * @param results search results to format
     * @return formatted string ready for prompt injection
     */
    public String formatAsContext(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\n--- 以下是联网搜索结果，请参考这些信息回答用户问题 ---\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            context.append(String.format("[%d] %s\n", i + 1, result.getTitle()));
            context.append(String.format("    来源: %s\n", result.getUrl()));
            context.append(String.format("    摘要: %s\n\n", result.getSnippet()));
        }

        context.append("--- 搜索结果结束 ---\n");
        return context.toString();
    }

    /**
     * Use LLM to extract search queries from user's message.
     * This helps generate more effective search queries.
     *
     * @param chatModel the LLM to use for query extraction
     * @param userMessage the user's original message
     * @return extracted search query
     */
    public String extractSearchQuery(ChatModel chatModel, String userMessage) {
        try {
            String systemInstruction = "你是一个搜索查询提取器。根据用户的问题，"
                    + "提取出最适合用于网络搜索的关键词查询。只输出搜索查询本身，"
                    + "不要有任何解释或附加文字。如果用户的问题本身就很适合搜索，直接返回即可。";

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemInstruction),
                    new UserMessage(userMessage)
            ));

            var response = chatModel.call(prompt);
            String query = response.getResult().getOutput().getText();
            return query != null ? query.trim() : userMessage;
        } catch (Exception e) {
            log.warn("Failed to extract search query via LLM, using original message: {}", e.getMessage());
            return userMessage;
        }
    }

    /**
     * Search result data object.
     */
    @Data
    public static class SearchResult implements Serializable {
        private final String title;
        private final String snippet;
        private final String url;
    }
}
