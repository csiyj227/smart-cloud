package com.smart.flow.infrastructure.compiler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.flow.api.dsl.FlowChartDsl;
import com.smart.flow.api.exception.FlowChartCompileException;
import com.smart.flow.domain.chart.CompiledArtifact;
import com.smart.flow.domain.chart.FlowChart;
import com.smart.flow.domain.chart.FlowChartValidator;
import com.smart.flow.infrastructure.config.FlowJacksonConfig;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The orchestrator of the FlowChart-to-BPMN compilation pipeline.
 *
 * <p>The pipeline is intentionally split into four discrete phases - {@code parse},
 * {@code validate}, {@code transform} and {@code emit} - each implemented in its own class so
 * that they can be tested and replaced independently. This is the main reason we did not
 * adopt the "single recursive builder" pattern seen in similar workflow products: separating
 * concerns is what allows the compiler to evolve without rewriting the whole thing.
 *
 * <ol>
 *   <li><strong>parse</strong> - turns the wire DSL into an indexed in-memory model
 *       ({@link FlowChart}); reports structural errors.</li>
 *   <li><strong>validate</strong> - enforces semantic rules (uniqueness of START, reachability,
 *       per-kind property requirements, ...).</li>
 *   <li><strong>transform</strong> - normalises node properties (defaults, type coercions,
 *       branch edge ordering) so the emitter has a tidy input.</li>
 *   <li><strong>emit</strong> - constructs an executable {@link BpmnModel} and serialises it
 *       to XML.</li>
 * </ol>
 */
@Slf4j
@Component
public class FlowChartCompiler {

    private final BpmnEmitter emitter;
    private final ChartTransformer transformer;
    private final ObjectMapper flowObjectMapper;

    /**
     * Constructor injection - keeping it explicit (rather than using
     * {@code @RequiredArgsConstructor}) so that the {@link Qualifier} on
     * {@code flowObjectMapper} is preserved on the constructor parameter, which is what
     * Spring inspects when there are multiple {@code ObjectMapper} beans in the context.
     */
    public FlowChartCompiler(BpmnEmitter emitter,
                             ChartTransformer transformer,
                             @Qualifier(FlowJacksonConfig.BEAN_NAME) ObjectMapper flowObjectMapper) {
        this.emitter = emitter;
        this.transformer = transformer;
        this.flowObjectMapper = flowObjectMapper;
    }

    /**
     * Compiles a chart DSL into an executable BPMN artifact.
     *
     * @throws FlowChartCompileException if any phase reports issues that prevent execution.
     */
    public CompiledArtifact compile(FlowChartDsl dsl) {
        if (dsl == null) {
            throw new FlowChartCompileException("DSL is null");
        }
        List<String> warnings = new ArrayList<>();

        // Phase 1 - parse
        FlowChart chart = FlowChart.parse(dsl);

        // Phase 2 - validate
        FlowChartValidator.validate(chart);

        // Phase 3 - transform (currently in-place mutation of the DSL nodes/edges; see class docs)
        transformer.normalise(chart, warnings);

        // Phase 4 - emit
        BpmnModel model = emitter.emit(chart);
        String xml = serialise(model);

        log.debug("Compiled chart {} into BPMN ({} bytes, {} warnings)",
                chart.getChartKey(), xml.length(), warnings.size());

        return new CompiledArtifact(model, xml, warnings);
    }

    /**
     * Compiles directly from a JSON string. Useful for the REST layer which receives the DSL
     * as a free-form payload.
     */
    public CompiledArtifact compileFromJson(String dslJson) {
        try {
            FlowChartDsl dsl = flowObjectMapper.readValue(dslJson, FlowChartDsl.class);
            return compile(dsl);
        } catch (JsonProcessingException e) {
            throw new FlowChartCompileException("DSL JSON is malformed: " + e.getOriginalMessage());
        }
    }

    private String serialise(BpmnModel model) {
        BpmnXMLConverter converter = new BpmnXMLConverter();
        byte[] bytes = converter.convertToXML(model, StandardCharsets.UTF_8.name());
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
