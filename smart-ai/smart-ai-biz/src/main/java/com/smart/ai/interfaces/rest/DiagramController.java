package com.smart.ai.interfaces.rest;

import com.smart.ai.api.dto.DiagramRenderCmd;
import com.smart.ai.application.DiagramService;
import com.smart.common.core.web.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图表渲染相关接口（仅 PlantUML 走后端代理；其他图表前端直接渲染）。
 *
 * 复用 ai_chat 权限：能用 AI 对话的用户就能渲染对话里的图，无需新建权限位。
 */
@RestController
@RequestMapping("/ai/diagram")
@RequiredArgsConstructor
@Tag(name = "AI Diagram")
public class DiagramController {

    private final DiagramService diagramService;

    @PostMapping("/plantuml")
    @Operation(summary = "Render PlantUML source to SVG via backend proxy")
    @PreAuthorize("@authz.hasPermission('ai_chat')")
    public ApiResult<String> renderPlantUml(@Valid @RequestBody DiagramRenderCmd cmd) {
        return ApiResult.success(diagramService.renderPlantUml(cmd.getSource()));
    }
}
