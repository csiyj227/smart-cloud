package com.smart.nl2sql.infrastructure.nl2sql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.nl2sql.api.enums.ChartType;
import com.smart.nl2sql.infrastructure.llm.LlmClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recommends a chart type and ECharts-friendly config based on the analyzed SQL and the data sample.
 *
 * <p>It first asks the LLM (via {@link LlmClient}); if that fails, falls back to a
 * deterministic rule that picks bar/line/pie/table from the dimension/measure shape.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChartRecommender {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Recommendation recommend(String userQuestion,
                                    SqlAnalyzer.AnalysisResult analysis,
                                    List<SqlExecutor.ColumnInfo> columns,
                                    List<Map<String, Object>> rows) {
        try {
            String prompt = promptBuilder.buildNl2ChartPrompt(userQuestion, analysis, columns, rows);
            String resp = llmClient.chat(null, prompt);
            Matcher m = JSON_BLOCK.matcher(resp);
            if (m.find()) {
                JsonNode node = objectMapper.readTree(m.group());
                String type = node.path("chartType").asText("table");
                String config = node.path("config").toString();
                return new Recommendation(normalize(type), config);
            }
        } catch (Exception e) {
            log.warn("LLM 推荐图表失败，降级使用规则推荐: {}", e.getMessage());
        }
        return ruleBased(analysis, rows);
    }

    private Recommendation ruleBased(SqlAnalyzer.AnalysisResult analysis, List<Map<String, Object>> rows) {
        int dimCount = analysis.getDimensions().size();
        int measureCount = analysis.getMeasures().size();
        ChartType type;
        if (dimCount == 0 || measureCount == 0) {
            type = ChartType.TABLE;
        } else if (dimCount == 1 && measureCount == 1 && rows.size() <= 8) {
            type = ChartType.PIE;
        } else if (dimCount == 1 && measureCount >= 1) {
            type = ChartType.BAR;
        } else {
            type = ChartType.TABLE;
        }
        return new Recommendation(type.getCode(), "{}");
    }

    private String normalize(String type) {
        if (type == null) {
            return ChartType.TABLE.getCode();
        }
        try {
            for (ChartType c : ChartType.values()) {
                if (c.getCode().equalsIgnoreCase(type.trim())) {
                    return c.getCode();
                }
            }
        } catch (Exception ignore) {
            // fall-through
        }
        return ChartType.TABLE.getCode();
    }

    @Data
    @AllArgsConstructor
    public static class Recommendation {
        private String chartType;
        private String chartConfig;
    }
}
