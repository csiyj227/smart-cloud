package com.smart.flow.infrastructure.compiler;

import com.smart.flow.api.dsl.FlowNodeDsl;
import com.smart.flow.domain.chart.FlowChart;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transform phase of the compiler.
 *
 * <p>Performs lightweight, idempotent normalisation that the emitter would otherwise have to
 * repeat for every node:
 * <ul>
 *   <li>fills in default values for missing optional properties (e.g. {@code multiMode = none}
 *       for an APPROVE node, {@code passRule = all} when missing);</li>
 *   <li>warns about ambiguous configuration that is technically valid (e.g. an APPROVE node
 *       declared as {@code multiMode = sequential} but with {@code passRule = ratio} - which
 *       is rarely meaningful but not strictly invalid).</li>
 * </ul>
 *
 * <p>BRANCH outgoing-edge ordering is <em>not</em> handled here: the {@link FlowChart} model
 * exposes adjacency lists as unmodifiable views, and the emitter applies the canonical
 * "default-edge-last, then by priority" sort just before serialising. Centralising that sort in
 * the emitter avoids two competing sources of truth.
 *
 * <p>The transform writes back into the DSL nodes' property maps in place. Since callers reuse
 * the same {@link FlowChart} reference for downstream phases, in-place property writes are the
 * cleanest way to keep the contract that "the parsed chart is the canonical model".
 */
@Component
public class ChartTransformer {

    public void normalise(FlowChart chart, List<String> warnings) {
        for (FlowNodeDsl node : chart.getNodes().values()) {
            switch (node.getKind()) {
                case APPROVE -> normaliseApprove(node, warnings);
                case START, END, NOTIFY, SCRIPT, BRANCH, PARALLEL, JOINT -> {
                    // No transform required at this stage.
                }
            }
        }
    }

    private void normaliseApprove(FlowNodeDsl node, List<String> warnings) {
        Map<String, Object> props = node.getProperties() != null
                ? node.getProperties()
                : new HashMap<>();
        node.setProperties(props);
        props.putIfAbsent("multiMode", "none");
        props.putIfAbsent("passRule", "all");
        Object passRule = props.get("passRule");
        Object multiMode = props.get("multiMode");
        if ("ratio".equals(passRule) && !props.containsKey("passRatio")) {
            warnings.add("APPROVE node " + node.getKey()
                    + " uses passRule=ratio but no passRatio is set; defaulting to 0.5");
            props.put("passRatio", 0.5);
        }
        if ("sequential".equals(multiMode) && "ratio".equals(passRule)) {
            warnings.add("APPROVE node " + node.getKey()
                    + " uses sequential multiMode with ratio passRule; consider using all/any instead");
        }
    }
}
