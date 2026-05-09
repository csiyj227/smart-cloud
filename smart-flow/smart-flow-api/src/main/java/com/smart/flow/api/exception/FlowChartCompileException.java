package com.smart.flow.api.exception;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when the FlowChart compiler refuses to turn a chart DSL into BPMN.
 *
 * <p>The exception is intentionally aggregating: rather than failing on the first issue, the
 * compiler collects every problem it can find during the {@code parse} and {@code validate}
 * phases and surfaces them all at once, so that the front-end can render every red squiggle in
 * a single round-trip instead of forcing the user into a tedious whack-a-mole.
 */
@Getter
public class FlowChartCompileException extends RuntimeException {

    private final List<String> issues;

    public FlowChartCompileException(String message) {
        super(message);
        this.issues = Collections.singletonList(message);
    }

    public FlowChartCompileException(List<String> issues) {
        super(joinForMessage(issues));
        this.issues = new ArrayList<>(issues);
    }

    private static String joinForMessage(List<String> issues) {
        if (issues == null || issues.isEmpty()) {
            return "FlowChart compilation failed";
        }
        return "FlowChart compilation failed with " + issues.size() + " issue(s): "
                + String.join(" | ", issues);
    }
}
