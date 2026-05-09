package com.smart.ai.interfaces.rest;

import com.smart.ai.api.dto.ChatMessageCmd;
import com.smart.ai.api.dto.ChatMessageVO;
import com.smart.ai.application.ChatService;
import com.smart.ai.application.ConversationService;
import com.smart.common.core.web.ApiResult;
import com.smart.common.security.component.PermissionEvaluator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * AI chat endpoints with SSE streaming support.
 */
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
@Tag(name = "AI Chat")
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;
    /**
     * 项目里没有静态 SecurityUtils 工具类，统一通过 PermissionEvaluator
     * （即 @PreAuthorize 中使用的 "@authz" Bean）获取当前登录用户。
     * 它内部从 SecurityContextHolder 解析 SmartUser / Jwt / OAuth2 三种 principal。
     */
    private final PermissionEvaluator authz;

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send message and receive streaming response")
    @PreAuthorize("@authz.hasPermission('ai_chat')")
    public Flux<ChatMessageVO> send(@Valid @RequestBody ChatMessageCmd cmd) {
        Long userId = authz.getCurrentUserId();
        return chatService.chat(cmd, userId);
    }

    @GetMapping("/conversations")
    @Operation(summary = "List user conversations")
    @PreAuthorize("@authz.hasPermission('ai_chat')")
    public ApiResult<?> listConversations(@RequestParam(defaultValue = "1") Integer current,
                                  @RequestParam(defaultValue = "20") Integer size) {
        Long userId = authz.getCurrentUserId();
        return ApiResult.success(conversationService.pageByUser(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), userId));
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "List messages in a conversation")
    @PreAuthorize("@authz.hasPermission('ai_chat')")
    public ApiResult<?> listMessages(@PathVariable Long id) {
        return ApiResult.success(conversationService.listMessages(id));
    }

    @PutMapping("/conversations/{id}/title")
    @Operation(summary = "Rename conversation")
    @PreAuthorize("@authz.hasPermission('ai_chat')")
    public ApiResult<?> renameConversation(@PathVariable Long id, @RequestParam String title) {
        conversationService.updateTitle(id, title);
        return ApiResult.success();
    }

    @PutMapping("/conversations/{id}/pin")
    @Operation(summary = "Toggle conversation pin")
    @PreAuthorize("@authz.hasPermission('ai_chat')")
    public ApiResult<?> togglePin(@PathVariable Long id) {
        conversationService.togglePin(id);
        return ApiResult.success();
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Delete conversation and all messages")
    @PreAuthorize("@authz.hasPermission('ai_chat')")
    public ApiResult<?> deleteConversation(@PathVariable Long id) {
        conversationService.delete(id);
        return ApiResult.success();
    }
}
