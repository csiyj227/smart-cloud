package com.smart.flow.api.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The wire-format definition of a process chart.
 *
 * <p>This is the front-end contract: the designer serialises a chart as this structure and
 * sends it to the back-end. The compiler turns it into BPMN. The chart is intentionally a
 * <strong>graph</strong> (nodes + edges) rather than a tree, which makes parallel fan-out / join
 * and free-form jumps natural to express - and is the main reason we did not adopt the linked
 * "child node" structure used by other workflow products.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowChartDsl {

    /** Stable business key, immutable across versions of the same chart. */
    private String chartKey;

    /** Human-readable name for display. */
    private String chartName;

    /** Optional category, for grouping in the chart catalog. */
    private String category;

    /** Free-form chart-level metadata (icon, colour, doc link, ...). */
    private Map<String, Object> metadata;

    /** Form bindings associated with this chart (dynamic / custom forms). */
    private List<FlowFormBindingDsl> forms;

    /** Every node, including exactly one START and at least one END. */
    private List<FlowNodeDsl> nodes = new ArrayList<>();

    /** Directed edges connecting node keys. */
    private List<FlowEdgeDsl> edges = new ArrayList<>();
}
