package com.smart.flow.api.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Enumerates the kinds of nodes recognised by the FlowChart DSL.
 *
 * <p>These are intentionally <em>verbs / abstract concepts</em> rather than the BPMN element
 * names (UserTask, ExclusiveGateway, ...) so that business users can reason about the chart
 * without learning BPMN, while the compiler is responsible for translating each kind into the
 * correct BPMN element on emit.
 *
 * <p>The on-the-wire form is always lower-case to keep the JSON DSL terse.
 */
public enum FlowNodeKind {

    /** The single entry point of a chart - the user who fills the form and submits it. */
    START("start"),

    /** A regular human approval step (single approver or multi-instance). */
    APPROVE("approve"),

    /** A non-blocking notification step (CC). The chart continues regardless. */
    NOTIFY("notify"),

    /** A conditional split with mutually-exclusive outgoing branches. */
    BRANCH("branch"),

    /** Parallel fan-out - all outgoing branches start simultaneously. */
    PARALLEL("parallel"),

    /** Parallel join - waits for all incoming branches to complete. */
    JOINT("joint"),

    /** A scripted/automated step (calls a registered ServiceTask handler). */
    SCRIPT("script"),

    /** The terminal node of the chart. */
    END("end");

    private final String wire;

    FlowNodeKind(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String getWire() {
        return wire;
    }

    @JsonCreator
    public static FlowNodeKind fromWire(String wire) {
        return Arrays.stream(values())
                .filter(k -> k.wire.equalsIgnoreCase(wire))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown node kind: " + wire));
    }
}
