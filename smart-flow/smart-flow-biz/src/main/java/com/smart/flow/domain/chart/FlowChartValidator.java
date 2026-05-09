package com.smart.flow.domain.chart;

import com.smart.flow.api.dsl.FlowEdgeDsl;
import com.smart.flow.api.dsl.FlowNodeDsl;
import com.smart.flow.api.dsl.FlowNodeKind;
import com.smart.flow.api.exception.FlowChartCompileException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validate phase of the compiler.
 *
 * <p>Checks <strong>semantic</strong> correctness once the chart has been structurally parsed:
 * <ul>
 *   <li>exactly one START, at least one END,</li>
 *   <li>every node is reachable from START and can reach an END,</li>
 *   <li>BRANCH nodes have at least two outgoing edges and a default,</li>
 *   <li>condition expressions only appear on edges leaving a BRANCH,</li>
 *   <li>APPROVE nodes declare an assignee strategy.</li>
 * </ul>
 *
 * <p>Following the same "collect-all-issues" philosophy as the parse phase, the validator never
 * short-circuits: every problem it discovers ends up in the resulting exception so the user
 * fixes them in one editing pass.
 */
public final class FlowChartValidator {

    private FlowChartValidator() {
    }

    public static void validate(FlowChart chart) {
        List<String> issues = new ArrayList<>();

        long startCount = chart.getNodes().values().stream()
                .filter(n -> n.getKind() == FlowNodeKind.START).count();
        long endCount = chart.getNodes().values().stream()
                .filter(n -> n.getKind() == FlowNodeKind.END).count();
        if (startCount != 1) {
            issues.add("chart must contain exactly one START node, found " + startCount);
        }
        if (endCount < 1) {
            issues.add("chart must contain at least one END node");
        }

        for (FlowNodeDsl node : chart.getNodes().values()) {
            checkPerNodeRules(chart, node, issues);
        }
        for (FlowEdgeDsl edge : chart.getEdges()) {
            checkPerEdgeRules(chart, edge, issues);
        }

        // Reachability check is only meaningful when there is exactly one start.
        if (startCount == 1) {
            checkReachability(chart, issues);
        }

        if (!issues.isEmpty()) {
            throw new FlowChartCompileException(issues);
        }
    }

    private static void checkPerNodeRules(FlowChart chart, FlowNodeDsl node, List<String> issues) {
        FlowNodeKind kind = node.getKind();
        int outDegree = chart.outgoingOf(node.getKey()).size();
        int inDegree = chart.incomingOf(node.getKey()).size();

        switch (kind) {
            case START -> {
                if (inDegree != 0) {
                    issues.add("START node " + node.getKey() + " must have no incoming edges");
                }
                if (outDegree != 1) {
                    issues.add("START node " + node.getKey() + " must have exactly one outgoing edge");
                }
            }
            case END -> {
                if (outDegree != 0) {
                    issues.add("END node " + node.getKey() + " must have no outgoing edges");
                }
                if (inDegree == 0) {
                    issues.add("END node " + node.getKey() + " is unreachable (no incoming edges)");
                }
            }
            case BRANCH -> {
                if (outDegree < 2) {
                    issues.add("BRANCH node " + node.getKey() + " requires at least 2 outgoing edges");
                }
                long unconditional = chart.outgoingOf(node.getKey()).stream()
                        .filter(e -> e.getCondition() == null || e.getCondition().isBlank())
                        .count();
                if (unconditional == 0) {
                    issues.add("BRANCH node " + node.getKey() + " requires one default (unconditional) outgoing edge");
                } else if (unconditional > 1) {
                    issues.add("BRANCH node " + node.getKey() + " has multiple default edges; only one allowed");
                }
            }
            case APPROVE, NOTIFY -> {
                if (node.getProperties() == null
                        || node.getProperties().get("assigneeStrategy") == null) {
                    issues.add(kind + " node " + node.getKey() + " requires an assigneeStrategy property");
                }
                if (kind == FlowNodeKind.APPROVE && outDegree < 1) {
                    issues.add("APPROVE node " + node.getKey() + " must have an outgoing edge");
                }
            }
            case SCRIPT -> {
                if (node.getProperties() == null
                        || node.getProperties().get("handlerName") == null) {
                    issues.add("SCRIPT node " + node.getKey() + " requires a handlerName property");
                }
            }
            case PARALLEL -> {
                if (outDegree < 2) {
                    issues.add("PARALLEL node " + node.getKey() + " requires at least 2 outgoing edges");
                }
            }
            case JOINT -> {
                if (inDegree < 2) {
                    issues.add("JOINT node " + node.getKey() + " requires at least 2 incoming edges");
                }
            }
        }
    }

    private static void checkPerEdgeRules(FlowChart chart, FlowEdgeDsl edge, List<String> issues) {
        FlowNodeDsl source = chart.nodeOf(edge.getFrom());
        if (source == null) {
            return;
        }
        boolean conditional = edge.getCondition() != null && !edge.getCondition().isBlank();
        if (conditional && source.getKind() != FlowNodeKind.BRANCH) {
            issues.add("edge " + edge.getFrom() + " -> " + edge.getTo()
                    + " carries a condition but its source is not a BRANCH node");
        }
    }

    /**
     * Forward + backward BFS to ensure every node is on at least one START → END path.
     */
    private static void checkReachability(FlowChart chart, List<String> issues) {
        FlowNodeDsl start = chart.getNodes().values().stream()
                .filter(n -> n.getKind() == FlowNodeKind.START)
                .findFirst()
                .orElse(null);
        if (start == null) {
            return;
        }

        Set<String> reachableFromStart = bfs(chart, start.getKey(), true);
        for (String key : chart.getNodes().keySet()) {
            if (!reachableFromStart.contains(key)) {
                issues.add("node " + key + " is unreachable from START");
            }
        }

        Set<String> canReachEnd = new HashSet<>();
        for (FlowNodeDsl end : chart.getNodes().values()) {
            if (end.getKind() == FlowNodeKind.END) {
                canReachEnd.addAll(bfs(chart, end.getKey(), false));
            }
        }
        for (String key : chart.getNodes().keySet()) {
            if (!canReachEnd.contains(key)) {
                issues.add("node " + key + " has no path to any END");
            }
        }
    }

    private static Set<String> bfs(FlowChart chart, String startKey, boolean forward) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startKey);
        visited.add(startKey);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<FlowEdgeDsl> next = forward
                    ? chart.outgoingOf(current)
                    : chart.incomingOf(current);
            for (FlowEdgeDsl edge : next) {
                String neighbour = forward ? edge.getTo() : edge.getFrom();
                if (visited.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return visited;
    }
}
