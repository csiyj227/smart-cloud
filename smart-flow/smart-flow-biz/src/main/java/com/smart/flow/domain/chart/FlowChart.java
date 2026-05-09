package com.smart.flow.domain.chart;

import com.smart.flow.api.dsl.FlowEdgeDsl;
import com.smart.flow.api.dsl.FlowNodeDsl;
import com.smart.flow.api.dsl.FlowNodeKind;
import com.smart.flow.api.exception.FlowChartCompileException;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The internal, fully-resolved representation of a chart used by the compiler.
 *
 * <p>It is constructed from a {@link com.smart.flow.api.dsl.FlowChartDsl} during the
 * <em>parse</em> phase and then carried through the rest of the pipeline. Compared to the DSL,
 * it offers:
 * <ul>
 *   <li>O(1) lookup of a node by its key,</li>
 *   <li>pre-computed adjacency lists (incoming / outgoing edges per node),</li>
 *   <li>defensive copies that protect downstream phases from mutation.</li>
 * </ul>
 *
 * <p>The class is package-private to the {@code domain.chart} package and the compiler in
 * {@code infrastructure.compiler}; no controller or service code outside the workflow module
 * should reference it directly. Outside callers use the DSL instead.
 */
@Getter
public class FlowChart {

    private final String chartKey;
    private final String chartName;
    private final Map<String, FlowNodeDsl> nodes;
    private final List<FlowEdgeDsl> edges;
    private final Map<String, List<FlowEdgeDsl>> outgoingByNode;
    private final Map<String, List<FlowEdgeDsl>> incomingByNode;

    private FlowChart(String chartKey,
                      String chartName,
                      Map<String, FlowNodeDsl> nodes,
                      List<FlowEdgeDsl> edges,
                      Map<String, List<FlowEdgeDsl>> outgoingByNode,
                      Map<String, List<FlowEdgeDsl>> incomingByNode) {
        this.chartKey = chartKey;
        this.chartName = chartName;
        this.nodes = nodes;
        this.edges = edges;
        this.outgoingByNode = outgoingByNode;
        this.incomingByNode = incomingByNode;
    }

    /**
     * Parse phase entry point. Builds an internal model from the wire DSL while collecting
     * every structural error encountered. If at least one structural issue exists, the chart
     * cannot be reasoned about and a {@link FlowChartCompileException} is raised immediately.
     */
    public static FlowChart parse(com.smart.flow.api.dsl.FlowChartDsl dsl) {
        List<String> issues = new ArrayList<>();
        if (dsl == null) {
            throw new FlowChartCompileException("DSL is null");
        }
        if (isBlank(dsl.getChartKey())) {
            issues.add("chartKey is required");
        }
        if (dsl.getNodes() == null || dsl.getNodes().isEmpty()) {
            issues.add("at least one node is required");
        }
        if (dsl.getEdges() == null) {
            issues.add("edges array is required (may be empty for a chart with a single node)");
        }
        if (!issues.isEmpty()) {
            throw new FlowChartCompileException(issues);
        }

        // Index nodes by key while detecting duplicates / missing keys.
        Map<String, FlowNodeDsl> indexed = new LinkedHashMap<>();
        for (FlowNodeDsl node : dsl.getNodes()) {
            if (node == null) {
                issues.add("encountered a null node entry");
                continue;
            }
            if (isBlank(node.getKey())) {
                issues.add("a node is missing its key");
                continue;
            }
            if (node.getKind() == null) {
                issues.add("node " + node.getKey() + " has no kind");
                continue;
            }
            if (indexed.put(node.getKey(), node) != null) {
                issues.add("duplicate node key: " + node.getKey());
            }
        }

        // Build adjacency lists, validating edge endpoints exist.
        Map<String, List<FlowEdgeDsl>> outgoing = new LinkedHashMap<>();
        Map<String, List<FlowEdgeDsl>> incoming = new LinkedHashMap<>();
        for (String key : indexed.keySet()) {
            outgoing.put(key, new ArrayList<>());
            incoming.put(key, new ArrayList<>());
        }
        List<FlowEdgeDsl> edges = new ArrayList<>();
        for (FlowEdgeDsl edge : dsl.getEdges()) {
            if (edge == null || isBlank(edge.getFrom()) || isBlank(edge.getTo())) {
                issues.add("edge with missing from/to");
                continue;
            }
            if (!indexed.containsKey(edge.getFrom())) {
                issues.add("edge references unknown source node: " + edge.getFrom());
                continue;
            }
            if (!indexed.containsKey(edge.getTo())) {
                issues.add("edge references unknown target node: " + edge.getTo());
                continue;
            }
            edges.add(edge);
            outgoing.get(edge.getFrom()).add(edge);
            incoming.get(edge.getTo()).add(edge);
        }

        if (!issues.isEmpty()) {
            throw new FlowChartCompileException(issues);
        }

        return new FlowChart(
                dsl.getChartKey(),
                dsl.getChartName(),
                Collections.unmodifiableMap(indexed),
                Collections.unmodifiableList(edges),
                lockNested(outgoing),
                lockNested(incoming));
    }

    public FlowNodeDsl nodeOf(String key) {
        return nodes.get(key);
    }

    public List<FlowEdgeDsl> outgoingOf(String nodeKey) {
        return outgoingByNode.getOrDefault(nodeKey, Collections.emptyList());
    }

    public List<FlowEdgeDsl> incomingOf(String nodeKey) {
        return incomingByNode.getOrDefault(nodeKey, Collections.emptyList());
    }

    /**
     * Returns the (assumed unique) START node, throwing if the chart has none or more than one.
     * The validate phase enforces uniqueness; this method is a safe shortcut for the emit phase.
     */
    public FlowNodeDsl requireStart() {
        List<FlowNodeDsl> starts = nodes.values().stream()
                .filter(n -> n.getKind() == FlowNodeKind.START)
                .collect(Collectors.toList());
        if (starts.size() != 1) {
            throw new FlowChartCompileException("expected exactly one START node, found " + starts.size());
        }
        return starts.get(0);
    }

    private static Map<String, List<FlowEdgeDsl>> lockNested(Map<String, List<FlowEdgeDsl>> raw) {
        Map<String, List<FlowEdgeDsl>> locked = new LinkedHashMap<>(raw.size());
        raw.forEach((k, v) -> locked.put(k, Collections.unmodifiableList(v)));
        return Collections.unmodifiableMap(locked);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
