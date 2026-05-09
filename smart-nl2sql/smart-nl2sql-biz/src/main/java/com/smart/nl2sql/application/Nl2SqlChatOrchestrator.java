package com.smart.nl2sql.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.nl2sql.api.dto.Nl2SqlChatCmd;
import com.smart.nl2sql.api.dto.Nl2SqlChatVO;
import com.smart.nl2sql.api.dto.Nl2SqlSessionDTO;
import com.smart.nl2sql.infrastructure.nl2sql.Nl2SqlEngine;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasourceEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlMessageEntity;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.slf4j.MDC;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Orchestrates the full NL2SQL chat round: persistence + engine invocation + result mapping.
 *
 * <p>Lives in the application layer because it composes multiple domain services
 * (chat / dataset / datasource) and the NL2SQL engine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Nl2SqlChatOrchestrator {

    private static final int SQL_STATUS_SUCCESS = 1;
    private static final int SQL_STATUS_FAILED = 2;

    private final Nl2SqlChatService chatService;
    private final DataSourceService dataSourceService;
    private final Nl2sqlDatasetMapper datasetMapper;
    private final Nl2SqlEngine nl2SqlEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Nl2SqlChatVO ask(Nl2SqlChatCmd cmd, Long userId) {
        Long sessionId = cmd.getSessionId();
        if (sessionId == null) {
            Nl2SqlSessionDTO sessionDTO = new Nl2SqlSessionDTO();
            sessionDTO.setDatasetId(cmd.getDatasetId());
            sessionDTO.setModelId(cmd.getModelId());
            sessionDTO.setTitle(truncate(cmd.getQuestion(), 30));
            sessionId = chatService.createSession(sessionDTO, userId);
        }

        // Persist user question
        chatService.addMessage(sessionId, "user", cmd.getQuestion());

        // Resolve datasource
        Nl2sqlDatasetEntity dataset = datasetMapper.selectById(cmd.getDatasetId());
        if (dataset == null) {
            throw new IllegalArgumentException("数据集不存在: " + cmd.getDatasetId());
        }
        Nl2sqlDatasourceEntity ds = dataSourceService.getById(dataset.getDatasourceId());
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataset.getDatasourceId());
        }

        // Run engine
        Nl2SqlEngine.Nl2SqlResult result = nl2SqlEngine.run(cmd.getDatasetId(), ds, cmd.getQuestion());

        // Persist assistant message
        Nl2sqlMessageEntity assistant = chatService.addMessage(sessionId, "assistant",
                result.isSuccess() ? "执行成功" : "执行失败");
        applyResult(assistant, result);
        chatService.updateMessage(assistant);

        return toVO(sessionId, assistant, result, cmd.getQuestion());
    }

    /**
     * Streaming variant of {@link #ask(Nl2SqlChatCmd, Long)}: pushes intermediate
     * stage events to the given {@link SseEmitter} as the engine progresses.
     *
     * <p>Important: persistence (assistant message create + final update) still happens
     * synchronously in the calling thread. We do <b>not</b> wrap this in {@code @Transactional}
     * because the SSE write loop can take many seconds (especially while waiting on the LLM
     * for the data-insight stage) and we don't want to hold a database transaction open
     * for that long. Instead, each persistence call (createSession / addMessage /
     * updateMessage) is its own short transaction inside the underlying service.
     */
    public void askStream(Nl2SqlChatCmd cmd, Long userId, SseEmitter emitter) {
        // Capture the MDC context (including traceId) from the HTTP request thread
        // so that log entries in the async SSE thread carry the same traceId.
        Map<String, String> parentMdc = MDC.getCopyOfContextMap();

        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "nl2sql-sse-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        }).execute(() -> {
            if (parentMdc != null) {
                MDC.setContextMap(parentMdc);
            }
            try {
                doAskStream(cmd, userId, emitter);
            } finally {
                MDC.clear();
            }
        });
    }

    private void doAskStream(Nl2SqlChatCmd cmd, Long userId, SseEmitter emitter) {
        try {
            Long sessionId = cmd.getSessionId();
            if (sessionId == null) {
                Nl2SqlSessionDTO sessionDTO = new Nl2SqlSessionDTO();
                sessionDTO.setDatasetId(cmd.getDatasetId());
                sessionDTO.setModelId(cmd.getModelId());
                sessionDTO.setTitle(truncate(cmd.getQuestion(), 30));
                sessionId = chatService.createSession(sessionDTO, userId);
                sendEvent(emitter, "session", Map.of("sessionId", sessionId));
            }
            final Long sid = sessionId;

            chatService.addMessage(sessionId, "user", cmd.getQuestion());

            Nl2sqlDatasetEntity dataset = datasetMapper.selectById(cmd.getDatasetId());
            if (dataset == null) {
                throw new IllegalArgumentException("数据集不存在: " + cmd.getDatasetId());
            }
            Nl2sqlDatasourceEntity ds = dataSourceService.getById(dataset.getDatasourceId());
            if (ds == null) {
                throw new IllegalArgumentException("数据源不存在: " + dataset.getDatasourceId());
            }

            // Pre-create assistant message so the front-end gets a stable messageId
            Nl2sqlMessageEntity assistant = chatService.addMessage(sid, "assistant", "处理中…");
            sendEvent(emitter, "message", Map.of("messageId", assistant.getId(), "sessionId", sid));

            Nl2SqlEngine.Nl2SqlResult result = nl2SqlEngine.runStreaming(
                    cmd.getDatasetId(), ds, cmd.getQuestion(),
                    stageEvent -> {
                        try {
                            Nl2SqlEngine.Nl2SqlResult snapshot = stageEvent.getSnapshot();
                            sendEvent(emitter, stageEvent.getStage().name().toLowerCase(),
                                    snapshotPayload(snapshot, sid, assistant.getId()));
                        } catch (Exception e) {
                            log.warn("Push SSE stage failed: {}", e.getMessage());
                        }
                    });

            applyResult(assistant, result);
            assistant.setContent(result.isSuccess() ? "执行成功" : "执行失败");
            chatService.updateMessage(assistant);

            sendEvent(emitter, "done", toVO(sid, assistant, result, cmd.getQuestion()));
            emitter.complete();
        } catch (Exception e) {
            log.error("askStream failed", e);
            try {
                sendEvent(emitter, "error", Map.of("message", e.getMessage() == null ? "unknown" : e.getMessage()));
            } catch (Exception ignore) {
                // best-effort
            }
            emitter.completeWithError(e);
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    /**
     * Build a compact payload for an intermediate stage. Some snapshots may not yet
     * have all fields populated (e.g. before SQL execution there are no rows).
     */
    private Map<String, Object> snapshotPayload(Nl2SqlEngine.Nl2SqlResult snapshot,
                                                Long sessionId,
                                                Long messageId) {
        Map<String, Object> m = new HashMap<>();
        m.put("sessionId", sessionId);
        m.put("messageId", messageId);
        m.put("sql", snapshot.getSql());
        m.put("rowCount", snapshot.getRowCount());
        m.put("executionTime", snapshot.getExecutionMillis());
        m.put("rows", snapshot.getRows());
        m.put("columns", snapshot.getColumns());
        m.put("chartType", snapshot.getChartType());
        m.put("chartConfig", snapshot.getChartConfig());
        m.put("dimensions", snapshot.getDimensionsJson());
        m.put("measures", snapshot.getMeasuresJson());
        m.put("dataInsight", snapshot.getDataInsight());
        m.put("errorMessage", snapshot.getErrorMessage());
        return m;
    }

    @Transactional
    public Nl2SqlChatVO editAndRerun(Long messageId, String newSql) {
        Nl2sqlMessageEntity message = requireMessage(messageId);
        Long sessionId = message.getSessionId();

        Nl2sqlDatasetEntity dataset = resolveDatasetBySession(sessionId);
        Nl2sqlDatasourceEntity ds = dataSourceService.getById(dataset.getDatasourceId());
        String userQuestion = previousUserQuestion(sessionId);

        Nl2SqlEngine.Nl2SqlResult result = nl2SqlEngine.rerun(ds, userQuestion, newSql);
        applyResult(message, result);
        chatService.updateMessage(message);
        return toVO(sessionId, message, result, userQuestion);
    }

    @Transactional
    public Nl2SqlChatVO recomputeChart(Long messageId, String forceChartType) {
        Nl2sqlMessageEntity message = requireMessage(messageId);
        if (forceChartType != null && !forceChartType.isBlank()) {
            message.setChartType(forceChartType);
            chatService.updateMessage(message);
        }
        Nl2SqlChatVO vo = new Nl2SqlChatVO();
        vo.setMessageId(messageId);
        vo.setSessionId(message.getSessionId());
        vo.setSql(message.getGeneratedSql());
        vo.setChartType(message.getChartType());
        vo.setChartConfig(message.getChartConfig());
        vo.setQueryResult(message.getQueryResult());
        vo.setResultCount(message.getResultCount());
        vo.setDimensions(message.getDimensions());
        vo.setMeasures(message.getMeasures());
        vo.setDataInsight(message.getDataInsight());
        vo.setContent(message.getContent());
        return vo;
    }

    private Nl2sqlMessageEntity requireMessage(Long messageId) {
        Nl2sqlMessageEntity m = chatService.getMessage(messageId);
        if (m == null) {
            throw new IllegalArgumentException("消息不存在: " + messageId);
        }
        return m;
    }

    private Nl2sqlDatasetEntity resolveDatasetBySession(Long sessionId) {
        Nl2SqlSessionDTO dto = chatService.getSessionDetail(sessionId);
        Nl2sqlDatasetEntity dataset = datasetMapper.selectById(dto.getDatasetId());
        if (dataset == null) {
            throw new IllegalArgumentException("会话关联的数据集不存在");
        }
        return dataset;
    }

    private String previousUserQuestion(Long sessionId) {
        // The latest user message in the session is the question we want to re-run against.
        return chatService.getMessages(sessionId).stream()
                .filter(m -> "user".equalsIgnoreCase(m.getType()))
                .map(Nl2SqlChatVO::getContent)
                .reduce((a, b) -> b)
                .orElse("");
    }

    private void applyResult(Nl2sqlMessageEntity message, Nl2SqlEngine.Nl2SqlResult result) {
        message.setGeneratedSql(result.getSql());
        message.setSqlStatus(result.isSuccess() ? SQL_STATUS_SUCCESS : SQL_STATUS_FAILED);
        message.setExecutionTime(toIntMillis(result.getExecutionMillis()));
        message.setResultCount(result.getRowCount());
        message.setQueryResult(toJson(result.getRows()));
        message.setChartType(result.getChartType());
        message.setChartConfig(result.getChartConfig());
        message.setDimensions(result.getDimensionsJson());
        message.setMeasures(result.getMeasuresJson());
        message.setDataInsight(result.getDataInsight());
        message.setErrorMessage(result.getErrorMessage());
    }

    private Nl2SqlChatVO toVO(Long sessionId, Nl2sqlMessageEntity message,
                              Nl2SqlEngine.Nl2SqlResult result, String userQuestion) {
        return Nl2SqlChatVO.builder()
                .type("assistant")
                .content(userQuestion)
                .sessionId(sessionId)
                .messageId(message.getId())
                .sql(result.getSql())
                .queryResult(message.getQueryResult())
                .resultCount(result.getRowCount())
                .executionTime(result.getExecutionMillis())
                .chartType(result.getChartType())
                .chartConfig(result.getChartConfig())
                .dimensions(result.getDimensionsJson())
                .measures(result.getMeasuresJson())
                .dataInsight(result.getDataInsight())
                .errorMessage(result.getErrorMessage())
                .build();
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("序列化结果集失败", e);
            return "[]";
        }
    }

    private Integer toIntMillis(Long millis) {
        if (millis == null) {
            return null;
        }
        return BigDecimal.valueOf(millis).intValueExact();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "新会话";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
