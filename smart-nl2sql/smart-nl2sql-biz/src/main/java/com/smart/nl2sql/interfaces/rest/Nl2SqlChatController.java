package com.smart.nl2sql.interfaces.rest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.common.core.web.ApiResult;
import com.smart.common.security.component.PermissionEvaluator;
import com.smart.nl2sql.api.dto.Nl2SqlChatCmd;
import com.smart.nl2sql.api.dto.Nl2SqlChatVO;
import com.smart.nl2sql.api.dto.Nl2SqlSessionDTO;
import com.smart.nl2sql.api.dto.SqlEditCmd;
import com.smart.nl2sql.application.Nl2SqlChatOrchestrator;
import com.smart.nl2sql.application.Nl2SqlChatService;
import com.smart.nl2sql.application.SuggestionService;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlSessionEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * NL2SQL chat endpoints: session lifecycle, sending NL questions, editing SQL,
 * switching chart type.
 */
@RestController
@RequestMapping("/nl2sql/chat")
@RequiredArgsConstructor
@Tag(name = "NL2SQL Chat")
public class Nl2SqlChatController {

    private final Nl2SqlChatService chatService;
    private final Nl2SqlChatOrchestrator chatOrchestrator;
    private final SuggestionService suggestionService;
    private final PermissionEvaluator authz;

    @GetMapping("/session/page")
    @Operation(summary = "Page sessions")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public ApiResult<Page<Nl2sqlSessionEntity>> pageSessions(@RequestParam(defaultValue = "1") Integer current,
                                                     @RequestParam(defaultValue = "20") Integer size,
                                                     @RequestParam(required = false) Long datasetId) {
        return ApiResult.success(chatService.page(new Page<>(current, size), datasetId));
    }

    @PostMapping("/session")
    @Operation(summary = "Create chat session")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public ApiResult<Long> createSession(@Valid @RequestBody Nl2SqlSessionDTO dto) {
        Long userId = authz.getCurrentUserId();
        return ApiResult.success(chatService.createSession(dto, userId));
    }

    @GetMapping("/session/{id}")
    @Operation(summary = "Get session detail")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public ApiResult<Nl2SqlSessionDTO> sessionDetail(@PathVariable Long id) {
        return ApiResult.success(chatService.getSessionDetail(id));
    }

    @PutMapping("/session/{id}/title")
    @Operation(summary = "Rename session")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public ApiResult<?> renameSession(@PathVariable Long id, @RequestParam String title) {
        chatService.updateSessionTitle(id, title);
        return ApiResult.success();
    }

    @DeleteMapping("/session/{id}")
    @Operation(summary = "Delete session and all messages")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public ApiResult<?> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(id);
        return ApiResult.success();
    }

    @GetMapping("/messages")
    @Operation(summary = "List messages of a session")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public ApiResult<?> listMessages(@RequestParam Long sessionId) {
        return ApiResult.success(chatService.getMessages(sessionId));
    }

    @PostMapping("/send")
    @Operation(summary = "Send NL question and run NL2SQL → execute → chart → insight")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public ApiResult<Nl2SqlChatVO> send(@Valid @RequestBody Nl2SqlChatCmd cmd) {
        Long userId = authz.getCurrentUserId();
        return ApiResult.success(chatOrchestrator.ask(cmd, userId));
    }

    /**
     * SSE streaming variant of {@code /send}.
     *
     * <p>Each pipeline stage (sql_generated / sql_executed / chart_recommended /
     * insight_generated / done) is pushed as a separate SSE event so the front-end
     * can progressively render SQL → table → chart → insight.
     *
     * <p>Timeout is set to 5 minutes to accommodate slow LLMs and long-running queries;
     * the SqlExecutor itself enforces a 30-second SQL timeout to bound resource use.
     */
    @PostMapping(value = "/send-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send NL question (SSE streaming)")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public SseEmitter sendStream(@Valid @RequestBody Nl2SqlChatCmd cmd) {
        Long userId = authz.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(5L * 60 * 1000);
        chatOrchestrator.askStream(cmd, userId, emitter);
        return emitter;
    }

    @GetMapping("/suggestions/{datasetId}")
    @Operation(summary = "Get AI-generated suggested questions for a dataset (cached 10min)")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public ApiResult<java.util.List<String>> suggestions(@PathVariable Long datasetId) {
        return ApiResult.success(suggestionService.getSuggestions(datasetId));
    }

    @PostMapping("/message/{messageId}/edit-sql")
    @Operation(summary = "Edit SQL of a message and re-execute")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public ApiResult<Nl2SqlChatVO> editSql(@PathVariable Long messageId, @Valid @RequestBody SqlEditCmd cmd) {
        return ApiResult.success(chatOrchestrator.editAndRerun(messageId, cmd.getSql()));
    }

    @PostMapping("/message/{messageId}/re-chart")
    @Operation(summary = "Re-recommend chart type for an existing result")
    @PreAuthorize("@authz.hasPermission('nl2sql_chat')")
    public ApiResult<Nl2SqlChatVO> reChart(@PathVariable Long messageId,
                                   @RequestParam(required = false) String chartType) {
        return ApiResult.success(chatOrchestrator.recomputeChart(messageId, chartType));
    }
}
