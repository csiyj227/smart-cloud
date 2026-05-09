package com.smart.flow.api.definition;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Outbound DTO returned by the definition query endpoints.
 *
 * <p>Deliberately shaped as an immutable record-like value so callers cannot mutate it; this
 * also makes it safe to cache. Heavy fields ({@code chartDsl}, {@code bpmnXml}) are exposed
 * separately via the detail endpoint to keep list payloads lean.
 */
@Value
@Builder
public class FlowDefinitionView {

    Long chartId;
    String chartKey;
    String chartName;
    String chartCategory;
    Integer chartVersion;
    String publishStatus;
    /** 流程图 DSL JSON —— 仅在详情接口返回，列表接口为 null 以保持响应体轻量。 */
    String chartDsl;
    String deploymentId;
    String processDefinitionId;
    Long boundFormId;
    String description;
    String icon;
    Integer sortOrder;
    String createBy;
    LocalDateTime createTime;
    String updateBy;
    LocalDateTime updateTime;
}
