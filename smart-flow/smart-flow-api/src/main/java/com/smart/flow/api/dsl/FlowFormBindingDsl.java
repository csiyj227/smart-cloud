package com.smart.flow.api.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Form binding entry inside a {@link FlowChartDsl}.
 *
 * <ul>
 *   <li><b>DYNAMIC</b> – a form maintained by the visual form designer, identified by {@code formId}.</li>
 *   <li><b>CUSTOM</b>  – a custom business page, identified by {@code submitUrl} / {@code viewUrl}.</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowFormBindingDsl {

    /** Binding type: DYNAMIC or CUSTOM. */
    private String type;

    /** Display name. */
    private String name;

    /** Dynamic form ID (only for type = DYNAMIC). */
    private Long formId;

    /** Custom form submit URL (only for type = CUSTOM). */
    private String submitUrl;

    /** Custom form view URL (only for type = CUSTOM). */
    private String viewUrl;
}
