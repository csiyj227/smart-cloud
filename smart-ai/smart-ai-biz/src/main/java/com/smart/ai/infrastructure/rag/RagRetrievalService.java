package com.smart.ai.infrastructure.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.ai.infrastructure.llm.ChatModelFactory;
import com.smart.ai.infrastructure.llm.EmbeddingModelFactory;
import com.smart.ai.infrastructure.persistence.entity.AiKnowledgeBaseEntity;
import com.smart.ai.infrastructure.persistence.entity.AiKnowledgeSegmentEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiKnowledgeBaseMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiKnowledgeSegmentMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * RAG (Retrieval-Augmented Generation) retrieval service.
 *
 * <p>Retrieves relevant knowledge segments from the vector store and formats
 * them as context for LLM prompt injection. This enables the AI to answer
 * questions based on domain-specific documents stored in the knowledge base.
 *
 * <p>Current implementation uses a keyword-based retrieval approach with LLM
 * re-ranking. When pgvector embeddings are configured, it will switch to
 * vector similarity search for better retrieval quality.
 *
 * <p>Future enhancements:
 * <ul>
 *   <li>Vector embedding via Spring AI EmbeddingModel</li>
 *   <li>Hybrid retrieval (keyword + vector)</li>
 *   <li>Re-ranking with cross-encoder models</li>
 *   <li>Multi-query retrieval for complex questions</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AiKnowledgeSegmentMapper segmentMapper;
    private final EmbeddingModelFactory embeddingModelFactory;
    private final VectorStoreJdbcHelper vectorStore;

    /**
     * Retrieve relevant knowledge segments for a user query.
     *
     * @param knowledgeBaseIds list of knowledge base IDs to search in
     * @param userQuery the user's question
     * @param chatModel optional ChatModel for keyword extraction (can be null)
     * @return list of relevant segments ranked by relevance
     */
    public List<RetrievalResult> retrieve(List<Long> knowledgeBaseIds, String userQuery, ChatModel chatModel) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }

        List<RetrievalResult> allResults = new ArrayList<>();
        int maxResults = 5;

        for (Long kbId : knowledgeBaseIds) {
            AiKnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
            if (kb == null || !"1".equals(kb.getStatus())) {
                continue;
            }
            int topK = kb.getTopK() != null ? kb.getTopK() : 5;
            maxResults = Math.max(maxResults, topK);

            // ---------- Path 1: vector search via pgvector (preferred) ----------
            boolean vectorOk = false;
            if (kb.getEmbeddingModelId() != null) {
                try {
                    EmbeddingModel embeddingModel = embeddingModelFactory.getOrCreate(kb.getEmbeddingModelId());
                    float[] queryVec = embeddingModel.embed(userQuery);

                    Double maxDistance = null;
                    if (kb.getSimilarityThreshold() != null) {
                        // similarity threshold ∈ [0,1] → max cosine distance = 1 - threshold
                        maxDistance = 1.0 - kb.getSimilarityThreshold().doubleValue();
                    }

                    List<VectorStoreJdbcHelper.VectorSearchHit> hits =
                            vectorStore.searchByVector(kbId, queryVec, topK, maxDistance);

                    for (VectorStoreJdbcHelper.VectorSearchHit hit : hits) {
                        // cosine distance → similarity (1 - distance), clamp to [0,1]
                        double similarity = Math.max(0.0, Math.min(1.0, 1.0 - hit.distance));
                        allResults.add(new RetrievalResult(
                                hit.id, kbId, kb.getKbName(), hit.documentId,
                                hit.content, similarity));
                    }
                    vectorOk = true;
                    log.debug("Vector retrieval kb={} hits={}", kbId, hits.size());
                } catch (Exception e) {
                    log.warn("Vector retrieval failed for kb={}, falling back to keyword: {}",
                            kbId, e.getMessage());
                }
            }

            // ---------- Path 2: keyword fallback ----------
            if (!vectorOk) {
                List<String> keywords = extractKeywords(userQuery, chatModel);
                List<AiKnowledgeSegmentEntity> segments = searchSegments(kbId, keywords, topK * 2);
                for (AiKnowledgeSegmentEntity seg : segments) {
                    double score = calculateRelevance(seg.getContent(), userQuery, keywords);
                    allResults.add(new RetrievalResult(
                            seg.getId(), kbId, kb.getKbName(), seg.getDocumentId(),
                            seg.getContent(), score));
                }
            }
        }

        return allResults.stream()
                .sorted(Comparator.comparingDouble(RetrievalResult::getRelevanceScore).reversed())
                .limit(maxResults)
                .toList();
    }

    /**
     * Format retrieval results as context for LLM prompt injection.
     */
    public String formatAsContext(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\n--- 以下是从知识库中检索到的相关内容，请参考这些信息回答用户问题 ---\n\n");

        for (int i = 0; i < results.size(); i++) {
            RetrievalResult result = results.get(i);
            context.append(String.format("[知识片段 %d] (来源: %s, 相关度: %.2f)\n",
                    i + 1, result.getKnowledgeBaseName(), result.getRelevanceScore()));
            context.append(result.getContent());
            context.append("\n\n");
        }

        context.append("--- 知识库检索结果结束 ---\n");
        return context.toString();
    }

    /**
     * Extract keywords from user query using LLM (or simple tokenization as fallback).
     */
    private List<String> extractKeywords(String userQuery, ChatModel chatModel) {
        if (chatModel != null) {
            try {
                String systemInstruction = "你是一个关键词提取器。从用户的问题中提取 3-5 个最重要的关键词，"
                        + "用逗号分隔。只输出关键词，不要有任何解释。";

                Prompt prompt = new Prompt(List.of(
                        new SystemMessage(systemInstruction),
                        new UserMessage(userQuery)
                ), OpenAiChatOptions.builder().maxTokens(50).temperature(0.0).build());

                var response = chatModel.call(prompt);
                String keywords = response.getResult().getOutput().getText();
                if (keywords != null && !keywords.isBlank()) {
                    return List.of(keywords.split("[,，、\\s]+"));
                }
            } catch (Exception e) {
                log.warn("Failed to extract keywords via LLM, using simple tokenization: {}", e.getMessage());
            }
        }

        // Fallback: simple tokenization by removing common stop words
        return simpleTokenize(userQuery);
    }

    /**
     * Simple keyword tokenization for Chinese/English mixed text.
     */
    private List<String> simpleTokenize(String text) {
        // Remove punctuation and split
        String cleaned = text.replaceAll("[\\p{Punct}\\s]+", " ").trim();
        String[] tokens = cleaned.split("\\s+");

        List<String> keywords = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() >= 2) {
                keywords.add(token);
            }
        }

        return keywords.stream().limit(5).toList();
    }

    /**
     * Search segments in a knowledge base using keyword matching (SQL LIKE).
     */
    private List<AiKnowledgeSegmentEntity> searchSegments(Long kbId, List<String> keywords, int limit) {
        if (keywords.isEmpty()) {
            // Fallback: return most recent segments
            return segmentMapper.selectList(new LambdaQueryWrapper<AiKnowledgeSegmentEntity>()
                    .eq(AiKnowledgeSegmentEntity::getKbId, kbId)
                    .eq(AiKnowledgeSegmentEntity::getStatus, "1")
                    .orderByAsc(AiKnowledgeSegmentEntity::getSegmentIndex)
                    .last("LIMIT " + limit));
        }

        // Build OR query with keywords
        LambdaQueryWrapper<AiKnowledgeSegmentEntity> wrapper = new LambdaQueryWrapper<AiKnowledgeSegmentEntity>()
                .eq(AiKnowledgeSegmentEntity::getKbId, kbId)
                .eq(AiKnowledgeSegmentEntity::getStatus, "1");

        wrapper.and(w -> {
            for (int i = 0; i < keywords.size(); i++) {
                if (i == 0) {
                    w.like(AiKnowledgeSegmentEntity::getContent, keywords.get(i));
                } else {
                    w.or().like(AiKnowledgeSegmentEntity::getContent, keywords.get(i));
                }
            }
        });

        wrapper.last("LIMIT " + limit);
        return segmentMapper.selectList(wrapper);
    }

    /**
     * Calculate keyword-based relevance score (0.0 ~ 1.0).
     */
    private double calculateRelevance(String content, String query, List<String> keywords) {
        if (content == null || content.isEmpty()) return 0.0;

        String lowerContent = content.toLowerCase();
        int matchCount = 0;

        for (String keyword : keywords) {
            if (lowerContent.contains(keyword.toLowerCase())) {
                matchCount++;
            }
        }

        double keywordScore = keywords.isEmpty() ? 0.0 : (double) matchCount / keywords.size();

        // Bonus for exact phrase match
        double phraseBonus = lowerContent.contains(query.toLowerCase()) ? 0.3 : 0.0;

        return Math.min(1.0, keywordScore * 0.7 + phraseBonus);
    }

    /**
     * Retrieval result data object.
     */
    @Data
    public static class RetrievalResult implements Serializable {
        private final Long segmentId;
        private final Long knowledgeBaseId;
        private final String knowledgeBaseName;
        private final Long documentId;
        private final String content;
        private final double relevanceScore;
    }
}
