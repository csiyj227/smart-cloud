package com.smart.ai.application;

import com.smart.ai.api.dto.ChatMessageCmd;
import com.smart.ai.api.dto.ChatMessageVO;
import com.smart.ai.infrastructure.llm.ChatModelFactory;
import com.smart.ai.infrastructure.mcp.McpToolInvokeService;
import com.smart.ai.infrastructure.persistence.entity.AiAgentEntity;
import com.smart.ai.infrastructure.persistence.entity.AiAgentKnowledgeEntity;
import com.smart.ai.infrastructure.persistence.entity.AiAgentToolEntity;
import com.smart.ai.infrastructure.persistence.entity.AiMessageEntity;
import com.smart.ai.infrastructure.persistence.entity.AiModelConfigEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiAgentKnowledgeMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiAgentMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiAgentToolMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiMessageMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiModelConfigMapper;
import com.smart.ai.infrastructure.rag.RagRetrievalService;
import com.smart.ai.infrastructure.search.WebSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core chat service that orchestrates LLM calls, streaming, and message persistence.
 *
 * <p>This is the central entry point for all AI conversation features including:
 * normal chat, deep thinking, web search, multimodal input, and tool calls.
 *
 * <p>The flow for each chat request:
 * <ol>
 *   <li>Create or reuse conversation</li>
 *   <li>Persist user message</li>
 *   <li>Build prompt (system + history + user message)</li>
 *   <li>Call LLM with streaming via Spring AI</li>
 *   <li>Stream response chunks as SSE events</li>
 *   <li>Persist assistant message on completion</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationService conversationService;
    private final ChatModelFactory chatModelFactory;
    private final WebSearchService webSearchService;
    private final RagRetrievalService ragRetrievalService;
    private final McpToolInvokeService mcpToolInvokeService;
    private final AiModelConfigMapper modelConfigMapper;
    private final AiAgentMapper agentMapper;
    private final AiAgentKnowledgeMapper agentKnowledgeMapper;
    private final AiAgentToolMapper agentToolMapper;
    private final AiMessageMapper messageMapper;

    /** Maximum number of history messages to include in the prompt context. */
    private static final int MAX_HISTORY_MESSAGES = 20;

    public Flux<ChatMessageVO> chat(ChatMessageCmd cmd, Long userId) {
        // Step 1: ensure conversation exists
        Long conversationId = cmd.getConversationId();
        if (conversationId == null) {
            conversationId = conversationService.createConversation(
                    userId, cmd.getModelConfigId(), cmd.getAgentId(),
                    truncateTitle(cmd.getContent()));
        }

        // Step 2: persist user message
        AiMessageEntity userMessage = new AiMessageEntity();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("USER");
        userMessage.setContent(cmd.getContent());
        userMessage.setContentType(
                cmd.getImageUrls() != null && !cmd.getImageUrls().isEmpty() ? "MIXED" : "TEXT");
        userMessage.setStatus("SUCCESS");
        messageMapper.insert(userMessage);

        // Step 3: resolve model config & optional agent
        Long modelConfigId = cmd.getModelConfigId();
        AiAgentEntity agent = cmd.getAgentId() != null ? agentMapper.selectById(cmd.getAgentId()) : null;

        // If agent specifies its own model, use that
        if (agent != null && agent.getModelConfigId() != null) {
            modelConfigId = agent.getModelConfigId();
        }

        AiModelConfigEntity modelConfig = modelConfigMapper.selectById(modelConfigId);
        if (modelConfig == null) {
            return Flux.just(buildErrorEvent(conversationId, "模型配置不存在，请检查设置"));
        }

        // Step 4: resolve knowledge base IDs bound to agent (for RAG)
        List<Long> knowledgeBaseIds = List.of();
        List<Long> mcpToolIds = List.of();
        if (agent != null) {
            knowledgeBaseIds = agentKnowledgeMapper.selectList(
                    Wrappers.<AiAgentKnowledgeEntity>lambdaQuery()
                            .eq(AiAgentKnowledgeEntity::getAgentId, agent.getId())
                            .orderByAsc(AiAgentKnowledgeEntity::getSortOrder)
            ).stream().map(AiAgentKnowledgeEntity::getKnowledgeBaseId).toList();

            // Resolve MCP tool IDs bound to agent (for Function Calling)
            mcpToolIds = agentToolMapper.selectList(
                    Wrappers.<AiAgentToolEntity>lambdaQuery()
                            .eq(AiAgentToolEntity::getAgentId, agent.getId())
                            .orderByAsc(AiAgentToolEntity::getSortOrder)
            ).stream().map(AiAgentToolEntity::getMcpToolId).toList();
        }

        // Step 5: build prompt messages (with web search & RAG context)
        List<Message> promptMessages = buildPromptMessages(conversationId, cmd, agent, knowledgeBaseIds);

        // Step 6: build chat options (may override temperature for agent, and attach MCP function callbacks)
        OpenAiChatOptions chatOptions = chatModelFactory.buildChatOptions(modelConfig);
        if (agent != null && agent.getTemperatureOverride() != null) {
            chatOptions = OpenAiChatOptions.builder()
                    .model(modelConfig.getModelCode())
                    .maxTokens(modelConfig.getMaxTokens())
                    .temperature(agent.getTemperatureOverride().doubleValue())
                    .topP(modelConfig.getTopP() != null ? modelConfig.getTopP().doubleValue() : null)
                    .build();
        }

        // Attach MCP tools as tool callbacks if model supports tool calling
        // (Spring AI 1.0 GA: FunctionCallback → ToolCallback rename)
        if (!mcpToolIds.isEmpty() && Boolean.TRUE.equals(modelConfig.getSupportFunctionCall())) {
            List<ToolCallback> toolCallbacks = mcpToolInvokeService.buildFunctionCallbacks(mcpToolIds);
            if (!toolCallbacks.isEmpty()) {
                chatOptions = mcpToolInvokeService.withFunctions(chatOptions, toolCallbacks);
                log.info("Attached {} MCP tool callbacks to chat options for agent {}",
                        toolCallbacks.size(), agent.getId());
            }
        }

        Prompt prompt = new Prompt(promptMessages, chatOptions);

        // Step 7: call LLM with streaming
        final Long finalConversationId = conversationId;
        final String finalModelCode = modelConfig.getModelCode();

        try {
            ChatModel chatModel = chatModelFactory.getOrCreate(modelConfigId);
            return streamFromModel(chatModel, prompt, finalConversationId, finalModelCode);
        } catch (Exception e) {
            log.error("Failed to create ChatModel for config {}: {}", modelConfigId, e.getMessage(), e);
            return Flux.just(buildErrorEvent(finalConversationId, "模型初始化失败: " + e.getMessage()));
        }
    }

    /**
     * Call the ChatModel's streaming API and convert the response into SSE events.
     *
     * <p>⚠️ 之前的实现用了 {@code Sinks.many().unicast()} + 自己手动 {@code subscribe()}，
     * 在 Spring MVC（Servlet 容器，非 WebFlux）下会有竞态：上游可能在 ResponseBodyEmitter
     * 还没订阅 sink 之前就已经把数据发完，导致前端永远收不到任何事件（"思考中..."不消失）。
     *
     * <p>正确做法：直接返回上游 Flux 的转换流，让 Spring MVC 的 ReactiveTypeHandler 来
     * 订阅它，副作用（累计 buffer / 写库）放进 doOnNext / doFinally。
     */
    private Flux<ChatMessageVO> streamFromModel(ChatModel chatModel, Prompt prompt,
                                                 Long conversationId, String modelCode) {
        StringBuilder contentBuffer = new StringBuilder();
        AtomicInteger tokenCount = new AtomicInteger(0);

        Flux<ChatMessageVO> contentFlux = chatModel.stream(prompt)
                .mapNotNull(chatResponse -> {
                    if (chatResponse == null || chatResponse.getResults().isEmpty()) {
                        return null;
                    }
                    var generation = chatResponse.getResult();
                    if (generation == null || generation.getOutput() == null) {
                        return null;
                    }

                    // 累计 token 用于结束事件 + 持久化
                    if (chatResponse.getMetadata() != null
                            && chatResponse.getMetadata().getUsage() != null) {
                        long total = chatResponse.getMetadata().getUsage().getTotalTokens();
                        if (total > 0) {
                            tokenCount.set((int) total);
                        }
                    }

                    String content = generation.getOutput().getText();
                    if (content == null || content.isEmpty()) {
                        return null;
                    }
                    contentBuffer.append(content);
                    return buildEvent(conversationId, null, "CONTENT", content, null, modelCode);
                })
                .onErrorResume(error -> {
                    log.error("LLM streaming error for conversation {}: {}",
                            conversationId, error.getMessage(), error);
                    AiMessageEntity errorMessage = new AiMessageEntity();
                    errorMessage.setConversationId(conversationId);
                    errorMessage.setRole("ASSISTANT");
                    errorMessage.setContentType("TEXT");
                    errorMessage.setModelCode(modelCode);
                    errorMessage.setStatus("ERROR");
                    errorMessage.setErrorMsg(error.getMessage());
                    messageMapper.insert(errorMessage);
                    return Flux.just(buildErrorEvent(conversationId, error.getMessage()));
                });

        // 流结束后：① 持久化助手消息 ② 追加 DONE 事件给前端
        return contentFlux
                .concatWith(Flux.defer(() -> {
                    persistAssistantMessage(conversationId, modelCode,
                            contentBuffer.toString(), "", tokenCount.get());
                    return Flux.just(buildDoneEvent(conversationId, tokenCount.get()));
                }));
    }

    /**
     * Build the prompt message list from conversation history, web search results,
     * RAG knowledge retrieval, and the current user message.
     */
    private List<Message> buildPromptMessages(Long conversationId, ChatMessageCmd cmd,
                                               AiAgentEntity agent, List<Long> knowledgeBaseIds) {
        List<Message> messages = new ArrayList<>();

        // 1. System message (from agent or default)
        StringBuilder systemPromptText = new StringBuilder(buildSystemPrompt(agent, cmd));

        // 2. Web search context (if enabled)
        boolean webSearchEnabled = cmd.isEnableWebSearch()
                || (agent != null && Boolean.TRUE.equals(agent.getEnableWebSearch()));
        if (webSearchEnabled) {
            try {
                var searchResults = webSearchService.search(cmd.getContent(), 5);
                String searchContext = webSearchService.formatAsContext(searchResults);
                if (!searchContext.isBlank()) {
                    systemPromptText.append(searchContext);
                }
            } catch (Exception e) {
                log.warn("Web search failed, proceeding without search results: {}", e.getMessage());
            }
        }

        // 3. RAG knowledge retrieval context (if agent has bound knowledge bases)
        if (!knowledgeBaseIds.isEmpty()) {
            log.info("RAG: agent {} has {} bound knowledge bases: {}",
                    agent != null ? agent.getId() : null, knowledgeBaseIds.size(), knowledgeBaseIds);
            try {
                var retrievalResults = ragRetrievalService.retrieve(knowledgeBaseIds, cmd.getContent(), null);
                log.info("RAG: retrieved {} segments for query \"{}\"",
                        retrievalResults.size(),
                        cmd.getContent().length() > 50 ? cmd.getContent().substring(0, 50) + "..." : cmd.getContent());
                if (retrievalResults.isEmpty()) {
                    // 极常见的踩坑：知识库里 ai_knowledge_segment 是空的（文档还没解析完成 / 解析失败 / embedding 维度不匹配）
                    log.warn("RAG: no segments retrieved. Possible causes: "
                            + "(1) document parse_status is FAILED — check ai_knowledge_document.error_msg; "
                            + "(2) segments table is empty — trigger reindex; "
                            + "(3) embedding dim mismatch — check ai_knowledge_segment.embedding column type vs ai.parse.embedding-dim.");
                }
                String ragContext = ragRetrievalService.formatAsContext(retrievalResults);
                if (!ragContext.isBlank()) {
                    systemPromptText.append(ragContext);
                    log.debug("RAG: injected {} chars of context into system prompt", ragContext.length());
                }
            } catch (Exception e) {
                log.warn("RAG retrieval failed, proceeding without knowledge context: {}", e.getMessage(), e);
            }
        } else if (agent != null) {
            log.debug("RAG: agent {} has no bound knowledge bases, skipping retrieval", agent.getId());
        }

        // Add assembled system message
        if (!systemPromptText.isEmpty()) {
            messages.add(new SystemMessage(systemPromptText.toString()));
        }

        // 4. Conversation history
        List<AiMessageEntity> history = conversationService.listMessages(conversationId);
        int startIndex = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = startIndex; i < history.size(); i++) {
            AiMessageEntity histMsg = history.get(i);
            switch (histMsg.getRole()) {
                case "USER" -> messages.add(new UserMessage(histMsg.getContent()));
                case "ASSISTANT" -> {
                    if (histMsg.getContent() != null && !histMsg.getContent().isBlank()) {
                        messages.add(new AssistantMessage(histMsg.getContent()));
                    }
                }
                default -> { /* skip SYSTEM, TOOL in history for now */ }
            }
        }

        // 5. Current user message (already persisted, add to prompt)
        messages.add(new UserMessage(cmd.getContent()));

        return messages;
    }

    /**
     * Build the base system prompt. Agent system prompt takes priority;
     * deep thinking hint is appended. Web search and RAG context are
     * injected separately in {@link #buildPromptMessages}.
     */
    /**
     * 文生图能力声明：告知 LLM 本系统支持的图表渲染引擎。
     * 前端会自动识别围栏代码块（```mermaid / ```plantuml / ```dot / ```flow / ```infographic）并渲染为可交互图表。
     * 如果不在 System Prompt 里声明，LLM 会认为用户没有渲染能力，只输出纯文本建议。
     */
    private static final String DIAGRAM_CAPABILITY_PROMPT = """

你具备「文生图」能力：当用户需要架构图、流程图、思维导图、时序图、类图、关系图、信息图等可视化内容时，\
请直接在回复中使用围栏代码块输出对应的 DSL 源码，系统会自动渲染为可交互的矢量图。

支持的图表语言及使用场景：
- ```mermaid：流程图(flowchart)、时序图(sequenceDiagram)、类图(classDiagram)、甘特图(gantt)、思维导图(mindmap)、状态图(stateDiagram)、ER图(erDiagram)、饼图(pie) —— 最推荐，覆盖面最广
- ```plantuml：UML 全家桶（时序图、用例图、活动图、组件图、部署图）—— 适合复杂 UML 场景
- ```dot：Graphviz DOT 语言 —— 适合拓扑图、依赖关系图、有向无向图
- ```flow：Flowchart.js 语法 —— 适合简单流程图
- ```infographic：ECharts JSON 配置 —— 适合数据可视化（柱状图、折线图、饼图、雷达图等）

使用规则：
1. 用户要求画图/生成图/可视化时，直接输出对应的围栏代码块，不要输出"我无法生成图片"之类的说明
2. 优先使用 mermaid（语法简洁、表达力强），复杂 UML 用 plantuml，数据图表用 infographic
3. 代码块内只放 DSL 源码，不要包含额外说明文字
4. 可以在代码块前后用文字解释图表内容和设计思路
""";

    private String buildSystemPrompt(AiAgentEntity agent, ChatMessageCmd cmd) {
        StringBuilder systemPrompt = new StringBuilder();

        if (agent != null && agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            systemPrompt.append(agent.getSystemPrompt());
        } else {
            systemPrompt.append("你是 Smart AI 智能助手，一个专业、友好、有帮助的 AI 对话助手。");
        }

        // 始终注入文生图能力声明，让 LLM 知道可以直接输出图表 DSL
        systemPrompt.append(DIAGRAM_CAPABILITY_PROMPT);

        boolean deepThinking = cmd.isEnableDeepThinking()
                || (agent != null && Boolean.TRUE.equals(agent.getEnableDeepThinking()));
        if (deepThinking) {
            systemPrompt.append("\n\n请进行深度思考，展示你的推理过程。先分析问题的各个方面，然后给出全面的回答。");
        }

        return systemPrompt.toString();
    }

    /**
     * Persist the completed assistant message and update conversation statistics.
     */
    private void persistAssistantMessage(Long conversationId, String modelCode,
                                          String content, String reasoningContent, int tokenCount) {
        AiMessageEntity assistantMessage = new AiMessageEntity();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setContent(content);
        assistantMessage.setContentType("TEXT");
        assistantMessage.setModelCode(modelCode);
        assistantMessage.setTokenCount(tokenCount);
        assistantMessage.setStatus("SUCCESS");

        if (reasoningContent != null && !reasoningContent.isEmpty()) {
            assistantMessage.setReasoningContent(reasoningContent);
        }

        messageMapper.insert(assistantMessage);
        conversationService.incrementMessageCount(conversationId, tokenCount);
    }

    private ChatMessageVO buildEvent(Long conversationId, Long messageId,
                                     String eventType, String content,
                                     String reasoningContent, String modelCode) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setConversationId(conversationId);
        vo.setMessageId(messageId);
        vo.setEventType(eventType);
        vo.setContent(content);
        vo.setReasoningContent(reasoningContent);
        vo.setModelCode(modelCode);
        return vo;
    }

    private ChatMessageVO buildDoneEvent(Long conversationId, int totalTokens) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setConversationId(conversationId);
        vo.setEventType("DONE");
        vo.setTotalTokens(totalTokens);
        return vo;
    }

    private ChatMessageVO buildErrorEvent(Long conversationId, String errorMsg) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setConversationId(conversationId);
        vo.setEventType("ERROR");
        vo.setErrorMsg(errorMsg);
        return vo;
    }

    private String truncateTitle(String content) {
        if (content == null) return "新对话";
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }
}
