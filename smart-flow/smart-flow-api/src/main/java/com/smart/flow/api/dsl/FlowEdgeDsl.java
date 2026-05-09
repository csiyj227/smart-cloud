package com.smart.flow.api.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * A directed edge between two {@link FlowNodeDsl}s.
 *
 * <p>The optional {@link #condition} field is meaningful only when the source node is a
 * {@code BRANCH}; in that case, the compiler turns it into the {@code conditionExpression} of
 * the outgoing BPMN sequence flow. The expression is a JUEL expression evaluated against the
 * process variables (typically the form fields).
 *
 * <p>Edges from non-branch nodes must have a null condition - the validator enforces this.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowEdgeDsl {

    /** Optional stable id of the edge - generated if absent. */
    private String key;

    /** Source node key. */
    private String from;

    /** Target node key. */
    private String to;

    /** Source handle ID on the canvas (e.g. "source-bottom", "source-right"). */
    private String sourceHandle;

    /** Target handle ID on the canvas (e.g. "target-top", "target-left"). */
    private String targetHandle;

    /** JUEL expression evaluated when leaving a BRANCH node, e.g. {@code ${leaveDays > 3}}. */
    private String condition;

    /** Display label for the canvas (e.g. "<= 3 days"). */
    private String label;

    /**
     * For BRANCH nodes, the lower the priority value the earlier the edge is evaluated. The
     * compiler sorts outgoing edges so that the first matching one wins.
     */
    private Integer priority;
}
