package com.smart.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图表渲染命令：前端把 DSL 源码丢过来，由后端代理调用渲染服务返回 SVG。
 *
 * 当前仅 PlantUML 走后端（Kroki 没开 CORS，且后续可能切到内网部署）。
 * Mermaid / Graphviz / Flowchart / Infographic 都在前端直接渲染，不会调用此接口。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiagramRenderCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** DSL 源码（PlantUML：可省略 @startuml/@enduml，后端会补齐） */
    @NotBlank(message = "Diagram source cannot be empty")
    private String source;
}
