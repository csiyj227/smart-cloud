package com.smart.nl2sql.application;

import com.smart.nl2sql.api.dto.DataSetDTO;
import com.smart.nl2sql.api.dto.DataSetTableDTO;
import com.smart.nl2sql.infrastructure.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 根据数据集元数据（表/字段）通过 AI 生成推荐问题，并缓存到 Redis。
 *
 * <p>缓存策略：每个数据集 10 分钟过期，缓存 key 格式 {@code nl2sql:suggest:{datasetId}}。
 * 前端选择数据集或新建会话时调用，快速返回"你可以这样问"的示例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private static final String CACHE_KEY_PREFIX = "nl2sql:suggest:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_SUGGESTIONS = 4;

    private final DataSetService dataSetService;
    private final LlmClient llmClient;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取数据集的推荐问题。优先走 Redis 缓存，miss 时调用 AI 生成并写入缓存。
     *
     * @param datasetId 数据集 ID
     * @return 推荐问题列表（最多 4 条）
     */
    @SuppressWarnings("unchecked")
    public List<String> getSuggestions(Long datasetId) {
        String cacheKey = CACHE_KEY_PREFIX + datasetId;

        // 先查缓存
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List) {
            return (List<String>) cached;
        }

        // 缓存未命中，AI 生成
        try {
            List<String> suggestions = generateSuggestions(datasetId);
            if (!suggestions.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, suggestions, CACHE_TTL);
            }
            return suggestions;
        } catch (Exception e) {
            log.warn("AI 生成推荐问题失败，datasetId={}: {}", datasetId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 手动清除某个数据集的推荐问题缓存（当数据集表/字段变更后可主动调用）。
     */
    public void evictCache(Long datasetId) {
        redisTemplate.delete(CACHE_KEY_PREFIX + datasetId);
    }

    private List<String> generateSuggestions(Long datasetId) {
        DataSetDTO detail = dataSetService.getDetail(datasetId);
        if (detail == null || detail.getTables() == null || detail.getTables().isEmpty()) {
            return Collections.emptyList();
        }

        String schemaDescription = buildSchemaDescription(detail);
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(schemaDescription);

        String response = llmClient.chat(systemPrompt, userPrompt);
        return parseResponse(response);
    }

    private String buildSchemaDescription(DataSetDTO detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("数据集名称：").append(detail.getName()).append("\n");
        if (detail.getDescription() != null && !detail.getDescription().isBlank()) {
            sb.append("数据集描述：").append(detail.getDescription()).append("\n");
        }
        sb.append("\n包含的数据表：\n");

        for (DataSetTableDTO table : detail.getTables()) {
            sb.append("- 表名：").append(table.getTableName());
            if (table.getTableComment() != null && !table.getTableComment().isBlank()) {
                sb.append("（").append(table.getTableComment()).append("）");
            }
            sb.append("\n");
            if (table.getColumns() != null) {
                table.getColumns().forEach(col -> {
                    sb.append("  · ").append(col.getColumnName());
                    String remark = col.getUserRemark();
                    if (remark == null || remark.isBlank()) {
                        remark = col.getColumnComment();
                    }
                    if (remark != null && !remark.isBlank()) {
                        sb.append("（").append(remark).append("）");
                    }
                    if (Boolean.TRUE.equals(col.getIsDimension())) {
                        sb.append(" [维度]");
                    }
                    if (Boolean.TRUE.equals(col.getIsMeasure())) {
                        sb.append(" [度量]");
                    }
                    sb.append("\n");
                });
            }
        }
        return sb.toString();
    }

    private String buildSystemPrompt() {
        return "你是一个数据分析助手。用户提供了一个数据集的表结构信息（包括表名、字段名、字段含义、维度/度量标记）。" +
                "你的任务是根据这些表结构信息，生成 " + MAX_SUGGESTIONS + " 个有代表性的、用户可能感兴趣的自然语言数据查询问题。\n\n" +
                "要求：\n" +
                "1. 问题必须与该数据集的实际表和字段相关，不要编造不存在的字段\n" +
                "2. 问题应覆盖不同的分析角度：趋势分析、排名 Top N、占比分布、对比等\n" +
                "3. 问题应简洁自然，像普通用户提问一样\n" +
                "4. 输出格式：每行一个问题，不要编号，不要其他多余文字";
    }

    private String buildUserPrompt(String schemaDescription) {
        return "以下是数据集的表结构信息：\n\n" + schemaDescription +
                "\n请生成 " + MAX_SUGGESTIONS + " 个有代表性的自然语言查询问题：";
    }

    private List<String> parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return Collections.emptyList();
        }
        return response.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                // 去掉可能的编号前缀，如 "1. " "1、" "- "
                .map(line -> line.replaceFirst("^\\d+[.、)）]\\s*", ""))
                .map(line -> line.replaceFirst("^[-·•]\\s*", ""))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && line.length() >= 4)
                .limit(MAX_SUGGESTIONS)
                .collect(Collectors.toList());
    }
}
