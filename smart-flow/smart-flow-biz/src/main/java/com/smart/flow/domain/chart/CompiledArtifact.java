package com.smart.flow.domain.chart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.flowable.bpmn.model.BpmnModel;

import java.util.List;

/**
 * Output of {@code FlowChartCompiler.compile(...)}.
 *
 * <p>Bundles the executable {@link BpmnModel} together with the serialised XML form (kept so
 * that callers can persist it without re-converting) and the diagnostic messages produced
 * during compilation. Warnings are non-fatal and do not block the deploy.
 */
@Getter
@AllArgsConstructor
public class CompiledArtifact {

    private final BpmnModel bpmnModel;
    private final String bpmnXml;
    private final List<String> warnings;
}
