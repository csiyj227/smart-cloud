package com.smart.nl2sql.infrastructure.nl2sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.nl2sql.infrastructure.llm.LlmClient;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasourceEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NL2SQL orchestration engine: builds prompt → calls LLM → validates SQL → executes →
 * analyzes dimensions/measures → recommends chart → produces data insight.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Nl2SqlEngine {

    private static final Pattern CODE_BLOCK = Pattern.compile("(?is)```(?:sql)?\\s*(.*?)```");

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final SqlAnalyzer sqlAnalyzer;
    private final SqlExecutor sqlExecutor;
    private final ChartRecommender chartRecommender;
    private final Nl2SqlContextBuilder contextBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Nl2SqlResult run(Long datasetId, Nl2sqlDatasourceEntity datasource, String userQuestion) {
        // Reuse the streaming pipeline but discard intermediate stage callbacks.
        return runStreaming(datasetId, datasource, userQuestion, stage -> { /* no-op */ });
    }

    /**
     * Streaming variant: invokes {@code onStage} every time a stage finishes,
     * so that the caller (e.g. a controller using {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter})
     * can push partial results to the client in real time.
     *
     * <p>Stages emitted (in order, when applicable):
     * <ol>
     *     <li>{@link Stage#PROMPT_BUILT}</li>
     *     <li>{@link Stage#SQL_GENERATED}</li>
     *     <li>{@link Stage#SQL_EXECUTED}</li>
     *     <li>{@link Stage#ANALYZED}</li>
     *     <li>{@link Stage#CHART_RECOMMENDED}</li>
     *     <li>{@link Stage#INSIGHT_GENERATED}</li>
     *     <li>{@link Stage#DONE}</li>
     * </ol>
     * On failure {@link Stage#FAILED} is emitted instead of the remaining stages.
     */
    public Nl2SqlResult runStreaming(Long datasetId,
                                     Nl2sqlDatasourceEntity datasource,
                                     String userQuestion,
                                     Consumer<StageEvent> onStage) {
        Nl2SqlResult result = new Nl2SqlResult();
        try {
            Nl2SqlContextBuilder.Context ctx = contextBuilder.build(datasetId);
            String systemPrompt = promptBuilder.buildNl2SqlSystemPrompt(
                    datasource.getType(), ctx.getTables(), ctx.getColumns(),
                    ctx.getRelations(), ctx.getKnowledge(), ctx.getSamples());
            emit(onStage, Stage.PROMPT_BUILT, result);

            String rawSql = llmClient.chat(systemPrompt, userQuestion);
            String sql = stripCodeBlock(rawSql);
            result.setSql(sql);
            emit(onStage, Stage.SQL_GENERATED, result);

            SqlExecutor.ExecutionResult exec = sqlExecutor.execute(datasource, sql);
            result.setExecutedSql(exec.getSql());
            result.setExecutionMillis(exec.getExecutionMillis());

            if (!exec.isSuccess()) {
                result.setSuccess(false);
                result.setErrorMessage(exec.getErrorMessage());
                emit(onStage, Stage.FAILED, result);
                return result;
            }

            result.setColumns(exec.getColumns());
            result.setRows(exec.getRows());
            result.setRowCount(exec.getRows().size());
            emit(onStage, Stage.SQL_EXECUTED, result);

            SqlAnalyzer.AnalysisResult analysis = sqlAnalyzer.analyze(sql);
            result.setAnalysis(analysis);
            result.setDimensionsJson(toJson(analysis.getDimensions()));
            result.setMeasuresJson(toJson(analysis.getMeasures()));
            emit(onStage, Stage.ANALYZED, result);

            ChartRecommender.Recommendation rec = chartRecommender.recommend(
                    userQuestion, analysis, exec.getColumns(), exec.getRows());
            result.setChartType(rec.getChartType());
            result.setChartConfig(rec.getChartConfig());
            emit(onStage, Stage.CHART_RECOMMENDED, result);

            try {
                String insightPrompt = promptBuilder.buildDataInsightPrompt(
                        userQuestion, sql, exec.getColumns(), exec.getRows());
                result.setDataInsight(llmClient.chat(null, insightPrompt));
                emit(onStage, Stage.INSIGHT_GENERATED, result);
            } catch (Exception ie) {
                log.warn("生成数据洞察失败: {}", ie.getMessage());
            }

            result.setSuccess(true);
            emit(onStage, Stage.DONE, result);
            return result;
        } catch (Exception e) {
            log.error("NL2SQL 执行失败 datasetId={}", datasetId, e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            try {
                emit(onStage, Stage.FAILED, result);
            } catch (Exception ignore) {
                // already logging the original failure
            }
            return result;
        }
    }

    private void emit(Consumer<StageEvent> onStage, Stage stage, Nl2SqlResult snapshot) {
        try {
            onStage.accept(new StageEvent(stage, snapshot));
        } catch (Exception e) {
            // A misbehaving listener should not break the pipeline.
            log.warn("Stage listener error stage={}", stage, e);
        }
    }

    /**
     * Re-execute the given SQL (e.g. after the user manually edited it) and re-derive
     * analysis / chart / insight. Skips the LLM SQL-generation step.
     */
    public Nl2SqlResult rerun(Nl2sqlDatasourceEntity datasource, String userQuestion, String sql) {
        Nl2SqlResult result = new Nl2SqlResult();
        result.setSql(sql);
        try {
            SqlExecutor.ExecutionResult exec = sqlExecutor.execute(datasource, sql);
            result.setExecutedSql(exec.getSql());
            result.setExecutionMillis(exec.getExecutionMillis());

            if (!exec.isSuccess()) {
                result.setSuccess(false);
                result.setErrorMessage(exec.getErrorMessage());
                return result;
            }

            result.setColumns(exec.getColumns());
            result.setRows(exec.getRows());
            result.setRowCount(exec.getRows().size());

            SqlAnalyzer.AnalysisResult analysis = sqlAnalyzer.analyze(sql);
            result.setAnalysis(analysis);
            result.setDimensionsJson(toJson(analysis.getDimensions()));
            result.setMeasuresJson(toJson(analysis.getMeasures()));

            ChartRecommender.Recommendation rec = chartRecommender.recommend(
                    userQuestion, analysis, exec.getColumns(), exec.getRows());
            result.setChartType(rec.getChartType());
            result.setChartConfig(rec.getChartConfig());

            result.setSuccess(true);
            return result;
        } catch (Exception e) {
            log.error("NL2SQL re-execute 失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    private String stripCodeBlock(String text) {
        if (text == null) {
            return "";
        }
        Matcher m = CODE_BLOCK.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return text.trim();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @Data
    @AllArgsConstructor
    public static class Nl2SqlResult {
        private boolean success;
        private String sql;
        private String executedSql;
        private List<SqlExecutor.ColumnInfo> columns;
        private List<java.util.Map<String, Object>> rows;
        private Integer rowCount;
        private Long executionMillis;
        private String chartType;
        private String chartConfig;
        private SqlAnalyzer.AnalysisResult analysis;
        private String dimensionsJson;
        private String measuresJson;
        private String dataInsight;
        private String errorMessage;

        public Nl2SqlResult() {
        }
    }

    /**
     * Pipeline stages exposed to streaming consumers (SSE).
     * Names are also used as SSE event names so the front-end can branch by stage.
     */
    public enum Stage {
        PROMPT_BUILT,
        SQL_GENERATED,
        SQL_EXECUTED,
        ANALYZED,
        CHART_RECOMMENDED,
        INSIGHT_GENERATED,
        DONE,
        FAILED
    }

    /** Snapshot of {@link Nl2SqlResult} associated with a stage. */
    @Data
    @AllArgsConstructor
    public static class StageEvent {
        private Stage stage;
        private Nl2SqlResult snapshot;
    }
}
