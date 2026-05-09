package com.smart.ai.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * 图表渲染服务：作为前端到 Kroki / 私有 PlantUML Server 的代理层。
 *
 * 选型：
 * - 不在 Java 端集成 plantuml.jar：plantuml 依赖 graphviz 二进制，部署侵入性大
 * - 选用 Kroki（https://kroki.io）：HTTP 接口，开箱即用；后续可换私有部署，
 *   只需改 application.yml 中的 smart.ai.diagram.kroki-base-url
 *
 * 安全：
 * - 限制源码长度（防御性，避免极端大体积 DSL 拖死下游）
 * - 渲染失败的异常抛给上层，由 GlobalExceptionHandler 统一兜底
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagramService {

    /** Kroki 服务地址。默认指向公网 https://kroki.io，可在 nacos 中覆盖切到内网部署 */
    @Value("${smart.ai.diagram.kroki-base-url:https://kroki.io}")
    private String krokiBaseUrl;

    /** 单次源码最大长度，防止恶意大 payload */
    private static final int MAX_SOURCE_LENGTH = 50_000;

    /** WebClient 懒加载并复用：每次新建会重新走连接池建立流程，性能差 */
    private volatile WebClient webClient;

    private WebClient getWebClient() {
        WebClient cached = this.webClient;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (this.webClient == null) {
                this.webClient = WebClient.builder().baseUrl(krokiBaseUrl).build();
            }
            return this.webClient;
        }
    }

    /**
     * 渲染 PlantUML 源码为 SVG 字符串。
     *
     * Kroki 协议：POST {baseUrl}/plantuml/svg，body 为原始 DSL 文本（text/plain）。
     * 与 GET 编码方式（base64+deflate）相比，POST 不需要前端编码，链路更简单。
     *
     * @param source 用户/LLM 输出的 PlantUML DSL；如果没有 @startuml/@enduml 包裹，
     *               会自动补齐（Kroki 对裸 DSL 接受度有限）
     * @return SVG 字符串
     */
    public String renderPlantUml(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("PlantUML source cannot be empty");
        }
        if (source.length() > MAX_SOURCE_LENGTH) {
            throw new IllegalArgumentException(
                    "PlantUML source too long: " + source.length() + " > " + MAX_SOURCE_LENGTH);
        }

        String normalized = normalizePlantUmlSource(source);

        try {
            String svg = getWebClient().post()
                    .uri("/plantuml/svg")
                    .contentType(MediaType.TEXT_PLAIN)
                    .accept(MediaType.valueOf("image/svg+xml"))
                    .bodyValue(normalized)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));

            if (svg == null || svg.isBlank()) {
                throw new IllegalStateException("Kroki returned empty SVG");
            }
            return svg;
        } catch (Exception e) {
            log.warn("PlantUML render failed via {}: {}", krokiBaseUrl, e.getMessage());
            throw new IllegalStateException("PlantUML 渲染失败：" + e.getMessage(), e);
        }
    }

    /**
     * 规范化 PlantUML 源码：自动补 @startuml/@enduml，避免裸 DSL 报错。
     * 用户已经写了的就不动。
     */
    private String normalizePlantUmlSource(String source) {
        String trimmed = source.strip();
        boolean hasStart = trimmed.contains("@startuml") || trimmed.contains("@startmindmap")
                || trimmed.contains("@startgantt") || trimmed.contains("@startwbs")
                || trimmed.contains("@startsalt") || trimmed.contains("@startjson")
                || trimmed.contains("@startyaml");
        if (hasStart) {
            return trimmed;
        }
        return "@startuml\n" + trimmed + "\n@enduml";
    }
}
