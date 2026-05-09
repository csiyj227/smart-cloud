package com.smart.flow.api.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

/**
 * A single node of a {@link FlowChartDsl}.
 *
 * <p>The shape of {@link #properties} depends on {@link #kind}:
 * <ul>
 *   <li>{@code APPROVE} → {@code assigneeStrategy}, {@code assigneeArgs},
 *       {@code multiMode} (none / parallel / sequential), {@code passRule}
 *       (any / all / ratio), {@code passRatio}, {@code formRules};</li>
 *   <li>{@code NOTIFY} → {@code assigneeStrategy}, {@code assigneeArgs};</li>
 *   <li>{@code BRANCH} → nothing - the rules live on outgoing edges.</li>
 *   <li>{@code SCRIPT} → {@code handlerName} (the registered ServiceTask bean name).</li>
 * </ul>
 *
 * <p>Keeping the property bag loosely-typed at the DSL boundary lets the front-end designer
 * evolve UI controls without forcing a back-end schema migration; the compiler validates the
 * required keys per kind during the {@code validate} phase.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowNodeDsl {

    /** Stable identifier within the chart. Used as BPMN element id after compilation. */
    private String key;

    /** Display label shown on the canvas and on the timeline. */
    private String label;

    /** Node kind - drives the BPMN element type emitted by the compiler. */
    private FlowNodeKind kind;

    /** Kind-specific configuration, see class-level docs. */
    private Map<String, Object> properties;

    /** Optional canvas position so the designer can re-render without auto-layout. */
    private Double x;
    private Double y;
}
