package com.smart.nl2sql.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.nl2sql.api.dto.ColumnAiEvaluateCmd;
import com.smart.nl2sql.api.dto.ColumnAiSuggestionVO;
import com.smart.nl2sql.infrastructure.llm.LlmClient;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetColumnEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetTableEntity;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetColumnMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetTableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 调用 LLM 对数据集字段进行业务含义评估，返回「建议」（不入库）。
 *
 * <p>设计要点：
 * <ul>
 *     <li>按表分批请求 LLM，避免单次 prompt 过大；同表字段一起评估能利用上下文相关性
 *     （如 user_id + user_name + user_age 应一致地标 user 主题）。</li>
 *     <li>默认只评估「user_remark 为空」的字段，不覆盖用户已维护内容。</li>
 *     <li>LLM 解析失败时返回的 VO 仅含字段元信息，suggestedRemark 为 null，
 *     前端可据此展示「评估失败」（不阻断整个评估）。</li>
 *     <li>单表字段数超过 {@link #MAX_COLUMNS_PER_PROMPT} 时按块切分，避免超出模型上下文窗口。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColumnAiEvaluator {

    private static final Pattern JSON_BLOCK = Pattern.compile("(?s)\\{.*\\}");

    /**
     * 单次 prompt 最多包含的字段数。超过此数量会按表内顺序切分为多个 LLM 调用。
     * 50 是经验值：以平均每字段描述 30 token 计算，加上 system prompt，单次约 2.5K token，
     * 远低于主流模型（qwen-plus 32K、gpt-4o 128K）的上限，且响应延迟可控。
     */
    private static final int MAX_COLUMNS_PER_PROMPT = 50;

    private final Nl2sqlDatasetMapper datasetMapper;
    private final Nl2sqlDatasetTableMapper tableMapper;
    private final Nl2sqlDatasetColumnMapper columnMapper;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ColumnAiSuggestionVO> evaluate(Long datasetId, ColumnAiEvaluateCmd cmd) {
        Nl2sqlDatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new IllegalArgumentException("数据集不存在: " + datasetId);
        }
        boolean onlyEmpty = cmd == null || cmd.getOnlyEmptyRemark() == null || cmd.getOnlyEmptyRemark();

        // 1. 取候选字段
        LambdaQueryWrapper<Nl2sqlDatasetColumnEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(Nl2sqlDatasetColumnEntity::getDatasetId, datasetId);
        if (cmd != null && cmd.getColumnIds() != null && !cmd.getColumnIds().isEmpty()) {
            qw.in(Nl2sqlDatasetColumnEntity::getId, cmd.getColumnIds());
        }
        if (cmd != null && cmd.getTableNames() != null && !cmd.getTableNames().isEmpty()) {
            qw.in(Nl2sqlDatasetColumnEntity::getTableName, cmd.getTableNames());
        }
        qw.orderByAsc(Nl2sqlDatasetColumnEntity::getTableName)
                .orderByAsc(Nl2sqlDatasetColumnEntity::getSortOrder);
        List<Nl2sqlDatasetColumnEntity> all = columnMapper.selectList(qw);

        List<Nl2sqlDatasetColumnEntity> candidates = onlyEmpty
                ? all.stream().filter(c -> isBlank(c.getUserRemark())).collect(Collectors.toList())
                : all;
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 2. 加载表注释作上下文（按表名 → comment）
        List<Nl2sqlDatasetTableEntity> tables = tableMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlDatasetTableEntity>()
                        .eq(Nl2sqlDatasetTableEntity::getDatasetId, datasetId));
        Map<String, String> tableCommentMap = new HashMap<>();
        for (Nl2sqlDatasetTableEntity t : tables) {
            tableCommentMap.put(t.getTableName(), pick(t.getTableAlias(), t.getTableComment()));
        }

        // 3. 按表分组逐组调 LLM
        Map<String, List<Nl2sqlDatasetColumnEntity>> grouped = candidates.stream()
                .collect(Collectors.groupingBy(Nl2sqlDatasetColumnEntity::getTableName, LinkedHashMap::new, Collectors.toList()));

        List<ColumnAiSuggestionVO> results = new ArrayList<>(candidates.size());
        for (Map.Entry<String, List<Nl2sqlDatasetColumnEntity>> e : grouped.entrySet()) {
            String tableName = e.getKey();
            List<Nl2sqlDatasetColumnEntity> cols = e.getValue();
            // 切分大表：超过 MAX_COLUMNS_PER_PROMPT 时分块调用，避免 prompt 过长
            // 同时把每个 chunk 的建议合并成一个 Map，下游统一从中查找
            Map<String, ColumnSuggestion> mergedSuggestions = new HashMap<>();
            boolean hasFailure = false;
            for (int from = 0; from < cols.size(); from += MAX_COLUMNS_PER_PROMPT) {
                int to = Math.min(from + MAX_COLUMNS_PER_PROMPT, cols.size());
                List<Nl2sqlDatasetColumnEntity> chunk = cols.subList(from, to);
                try {
                    mergedSuggestions.putAll(
                            callLlmForTable(tableName, tableCommentMap.get(tableName), chunk));
                } catch (RuntimeException ex) {
                    hasFailure = true;
                    log.warn("AI 评估表 {} (chunk {}-{}) 失败：{}",
                            tableName, from, to, ex.getMessage());
                }
            }
            // 无论成功失败，每个候选字段都要返回一条 VO（缺失的字段 suggestedRemark 为 null，
            // 前端可识别"评估失败"）
            for (Nl2sqlDatasetColumnEntity col : cols) {
                ColumnAiSuggestionVO vo = new ColumnAiSuggestionVO();
                vo.setColumnId(col.getId());
                vo.setTableName(col.getTableName());
                vo.setColumnName(col.getColumnName());
                vo.setColumnType(col.getColumnType());
                vo.setCurrentUserRemark(col.getUserRemark());
                ColumnSuggestion s = mergedSuggestions.get(col.getColumnName());
                if (s != null) {
                    vo.setSuggestedRemark(s.remark);
                    vo.setSuggestedIsDimension(s.isDimension);
                    vo.setSuggestedIsMeasure(s.isMeasure);
                }
                results.add(vo);
            }
            if (hasFailure) {
                log.warn("AI 评估表 {} 部分字段失败，已返回降级结果（无建议字段表现为 suggestedRemark=null）",
                        tableName);
            }
        }
        return results;
    }

    private Map<String, ColumnSuggestion> callLlmForTable(String tableName,
                                                          String tableComment,
                                                          List<Nl2sqlDatasetColumnEntity> cols) {
        String systemPrompt = """
                你是数据库字段元数据专家。请根据表名、表注释、字段名、字段类型、原注释，
                推断每个字段的业务含义、单位、可能的枚举值，并标记是否适合作为「维度」（用于分组、筛选）
                或「度量」（用于聚合计算）。

                输出严格的 JSON：
                {
                  "字段名1": {"remark": "业务含义说明（含单位/枚举值）", "isDimension": true|false, "isMeasure": true|false},
                  "字段名2": {...}
                }

                规则：
                1. remark 简明扼要，不超过 60 字
                2. 数值型字段（int/decimal/float 等）通常是度量；id/状态/类型/名称/日期等是维度
                3. 主键 id 字段一般既不是维度也不是度量
                4. 严格只输出 JSON，不要任何解释、markdown 标记
                """;

        StringBuilder user = new StringBuilder();
        user.append("表：").append(tableName).append("\n");
        if (!isBlank(tableComment)) {
            user.append("表说明：").append(tableComment).append("\n");
        }
        user.append("\n字段列表：\n");
        for (Nl2sqlDatasetColumnEntity c : cols) {
            user.append("- `").append(c.getColumnName()).append("` ")
                    .append(c.getColumnType());
            if (!isBlank(c.getColumnComment())) {
                user.append(" (原注释：").append(c.getColumnComment()).append(")");
            }
            if (Boolean.TRUE.equals(c.getIsPrimaryKey())) {
                user.append(" [主键]");
            }
            user.append("\n");
        }
        user.append("\n请输出对应的 JSON。");

        String resp = llmClient.chat(systemPrompt, user.toString());
        return parseSuggestions(resp);
    }

    private Map<String, ColumnSuggestion> parseSuggestions(String llmResponse) {
        Map<String, ColumnSuggestion> result = new HashMap<>();
        if (isBlank(llmResponse)) {
            return result;
        }
        // LLM 偶尔会用 ```json ... ``` 包裹，先提取出 {...} 主体
        String json = llmResponse;
        Matcher m = JSON_BLOCK.matcher(llmResponse);
        if (m.find()) {
            json = m.group();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            root.fields().forEachRemaining(entry -> {
                String columnName = entry.getKey();
                JsonNode node = entry.getValue();
                ColumnSuggestion s = new ColumnSuggestion();
                s.remark = node.path("remark").asText(null);
                s.isDimension = node.has("isDimension") ? node.get("isDimension").asBoolean(false) : null;
                s.isMeasure = node.has("isMeasure") ? node.get("isMeasure").asBoolean(false) : null;
                result.put(columnName, s);
            });
        } catch (JsonProcessingException e) {
            log.warn("解析 AI 返回 JSON 失败，原文 = {}", llmResponse, e);
        }
        return result;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String pick(String first, String fallback) {
        return !isBlank(first) ? first : fallback;
    }

    /** LLM 解析后的内部表示（不暴露给外部） */
    private static class ColumnSuggestion {
        String remark;
        Boolean isDimension;
        Boolean isMeasure;
    }
}
