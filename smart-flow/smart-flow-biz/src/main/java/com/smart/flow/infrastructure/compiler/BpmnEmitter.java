package com.smart.flow.infrastructure.compiler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.flow.api.dsl.FlowEdgeDsl;
import com.smart.flow.api.dsl.FlowNodeDsl;
import com.smart.flow.api.dsl.FlowNodeKind;
import com.smart.flow.domain.chart.FlowChart;
import com.smart.flow.infrastructure.config.FlowJacksonConfig;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Emit phase of the compiler.
 *
 * <p>Walks the validated {@link FlowChart} and builds an executable {@link BpmnModel}. The
 * mapping between FlowChart node kinds and BPMN elements is intentionally narrow and explicit,
 * so that anyone reading this file can immediately tell what each chart concept becomes at
 * runtime:
 *
 * <table>
 *   <tr><th>FlowChart kind</th><th>BPMN element</th></tr>
 *   <tr><td>START</td><td>StartEvent</td></tr>
 *   <tr><td>APPROVE</td><td>UserTask (+ optional MultiInstance)</td></tr>
 *   <tr><td>NOTIFY</td><td>ServiceTask wired to handler {@code flowNotifyHandler}</td></tr>
 *   <tr><td>SCRIPT</td><td>ServiceTask wired to the handler named in node properties</td></tr>
 *   <tr><td>BRANCH</td><td>ExclusiveGateway</td></tr>
 *   <tr><td>PARALLEL</td><td>ParallelGateway (diverging)</td></tr>
 *   <tr><td>JOINT</td><td>ParallelGateway (converging)</td></tr>
 *   <tr><td>END</td><td>EndEvent</td></tr>
 * </table>
 *
 * <p>Each emitted UserTask carries:
 * <ul>
 *   <li>a {@code smart:nodeKey} extension attribute - links a runtime task back to its chart
 *       node so dashboards / SQL queries that bypass the engine can still resolve a friendly
 *       name without re-parsing the DSL;</li>
 *   <li>when an SPI strategy is declared, a {@code create}-event task listener delegating to
 *       {@code ${assigneeRoutingTaskListener}}, with two {@code <flowable:field>} entries
 *       ({@link #FIELD_STRATEGY} + {@link #FIELD_PARAMS_JSON}) that the listener reads via
 *       Flowable's standard expression-injection mechanism. This keeps the strategy out of
 *       process variables and lets parallel branches each carry their own resolver config.</li>
 * </ul>
 */
@Component
public class BpmnEmitter {

    /** XML namespace used for the Smart-specific extension attributes on UserTasks. */
    public static final String SMART_NS = "http://smart.com/flow";

    /**
     * Spring bean name of the listener that resolves assignees at task creation time.
     * Wired into BPMN via {@code delegateExpression} so Flowable's Spring expression manager
     * can pick it up - keeps us free of {@code SpringContextHolder.getBean(...)} hacks.
     */
    public static final String LISTENER_BEAN = "assigneeRoutingTaskListener";

    /** FieldExtension name for the resolver strategy key. Read by AssigneeRoutingTaskListener. */
    public static final String FIELD_STRATEGY = "strategy";
    /** FieldExtension name for the JSON-encoded resolver parameters. */
    public static final String FIELD_PARAMS_JSON = "paramsJson";

    private final ObjectMapper objectMapper;

    public BpmnEmitter(@Qualifier(FlowJacksonConfig.BEAN_NAME) ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BpmnModel emit(FlowChart chart) {
        BpmnModel model = new BpmnModel();
        model.setTargetNamespace(SMART_NS);

        Process process = new Process();
        process.setId(chart.getChartKey());
        process.setName(chart.getChartName() != null ? chart.getChartName() : chart.getChartKey());
        process.setExecutable(true);
        model.addProcess(process);

        for (FlowNodeDsl node : chart.getNodes().values()) {
            FlowElement element = toBpmnElement(node);
            process.addFlowElement(element);
        }

        for (FlowNodeDsl source : chart.getNodes().values()) {
            List<FlowEdgeDsl> outgoing = chart.outgoingOf(source.getKey());
            // BRANCH outgoing must respect the same ordering rule used by the transformer.
            if (source.getKind() == FlowNodeKind.BRANCH) {
                outgoing = outgoing.stream()
                        .sorted(Comparator
                                .comparing((FlowEdgeDsl e) -> e.getCondition() == null
                                        || e.getCondition().isBlank())
                                .thenComparing(e -> e.getPriority() == null
                                        ? Integer.MAX_VALUE : e.getPriority()))
                        .toList();
            }
            for (FlowEdgeDsl edge : outgoing) {
                process.addFlowElement(toSequenceFlow(edge));
            }
        }
        return model;
    }

    private FlowElement toBpmnElement(FlowNodeDsl node) {
        return switch (node.getKind()) {
            case START -> emitStart(node);
            case END -> emitEnd(node);
            case APPROVE -> emitApprove(node);
            case NOTIFY -> emitNotify(node);
            case SCRIPT -> emitScript(node);
            case BRANCH -> emitExclusive(node);
            case PARALLEL -> emitParallel(node);
            case JOINT -> emitJoint(node);
        };
    }

    private StartEvent emitStart(FlowNodeDsl node) {
        StartEvent event = new StartEvent();
        applyId(event, node);
        return event;
    }

    private EndEvent emitEnd(FlowNodeDsl node) {
        EndEvent event = new EndEvent();
        applyId(event, node);
        return event;
    }

    private UserTask emitApprove(FlowNodeDsl node) {
        UserTask task = new UserTask();
        applyId(task, node);
        Map<String, Object> props = node.getProperties() == null ? Map.of() : node.getProperties();

        // Tag the task with the chart node key as a free-form attribute so dashboards / SQL
        // queries that bypass the engine can still link tasks back to a chart node. Listeners
        // themselves use task.getTaskDefinitionKey(), but the attribute is cheap and helpful.
        task.addAttribute(buildAttribute("nodeKey", node.getKey()));

        // We deliberately do NOT set task.setAssignee("${flowAssignee}") any more. Setting an
        // expression here makes Flowable evaluate it eagerly and fail with "no value found"
        // when the listener has not yet populated the variable. Instead we leave assignee
        // null and let AssigneeRoutingTaskListener call delegateTask.setAssignee(...) on the
        // 'create' event - which is exactly what the SPI was designed for.

        // Hook the resolver listener onto every approve node so the SPI is actually invoked.
        // The fields carry the resolver strategy + parameters via Flowable's standard
        // FieldExtension mechanism (which the listener reads through expression injection),
        // avoiding the brittle "stash everything into process variables" pattern.
        Object strategy = props.get("assigneeStrategy");
        if (strategy != null && !strategy.toString().isBlank()) {
            FlowableListener listener = new FlowableListener();
            listener.setEvent("create");
            listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
            listener.setImplementation("${" + LISTENER_BEAN + "}");

            List<FieldExtension> fields = new ArrayList<>(2);
            fields.add(stringField(FIELD_STRATEGY, strategy.toString()));
            fields.add(stringField(FIELD_PARAMS_JSON, encodeParams(props.get("assigneeArgs"))));
            listener.setFieldExtensions(fields);

            List<FlowableListener> listeners = new ArrayList<>(
                    task.getTaskListeners() == null ? Collections.emptyList() : task.getTaskListeners());
            listeners.add(listener);
            task.setTaskListeners(listeners);
        } else {
            // No SPI strategy declared -> the chart author must have set a hard-coded
            // assignee/candidates list under properties.assignee / properties.candidateUsers.
            // Honour that by writing a literal value (not an expression) so Flowable assigns
            // the task without any extra plumbing.
            applyStaticAssignment(task, props);
        }

        String multiMode = String.valueOf(props.getOrDefault("multiMode", "none"));
        if (!"none".equalsIgnoreCase(multiMode)) {
            MultiInstanceLoopCharacteristics multi = new MultiInstanceLoopCharacteristics();
            multi.setSequential("sequential".equalsIgnoreCase(multiMode));
            multi.setInputDataItem("${flowCandidates}");
            multi.setElementVariable("flowAssignee");
            multi.setCompletionCondition(buildCompletionCondition(props));
            task.setLoopCharacteristics(multi);
        }
        return task;
    }

    private void applyStaticAssignment(UserTask task, Map<String, Object> props) {
        Object hardcodedAssignee = props.get("assignee");
        if (hardcodedAssignee != null && !hardcodedAssignee.toString().isBlank()) {
            task.setAssignee(hardcodedAssignee.toString());
            return;
        }
        Object candidates = props.get("candidateUsers");
        if (candidates instanceof List<?> list && !list.isEmpty()) {
            task.setCandidateUsers(list.stream().map(Object::toString).toList());
        }
    }

    private FieldExtension stringField(String name, String value) {
        FieldExtension field = new FieldExtension();
        field.setFieldName(name);
        // Use the string-value channel rather than expression: prevents Flowable from
        // trying to evaluate the parameter JSON as an EL expression at parse time.
        field.setStringValue(value == null ? "" : value);
        return field;
    }

    private String encodeParams(Object rawArgs) {
        if (rawArgs == null) {
            return "{}";
        }
        // String input is the most common shape (the designer pastes a JSON literal). We
        // round-trip it through the parser so an invalid payload fails NOW with a clear error,
        // rather than later inside the listener where the only signal is "task created with no
        // assignees" and ops have to dig through warn logs to find out why. The re-serialise
        // also normalises whitespace - cheap and removes a class of subtle BPMN diff churn.
        if (rawArgs instanceof String s) {
            if (s.isBlank()) {
                return "{}";
            }
            try {
                Object parsed = objectMapper.readValue(s, Object.class);
                return objectMapper.writeValueAsString(parsed);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(
                        "assigneeArgs is not valid JSON: " + e.getOriginalMessage(), e);
            }
        }
        // For Map / List / scalar values, encode through the workflow ObjectMapper so the
        // wire format stays consistent with everything else the module produces.
        try {
            return objectMapper.writeValueAsString(rawArgs);
        } catch (JsonProcessingException e) {
            // The chart was already validated by the compiler; an encoding failure here means
            // a programming bug in the caller, not user input. Fail loudly.
            throw new IllegalStateException("Cannot encode assigneeArgs for BPMN emission", e);
        }
    }

    private ServiceTask emitNotify(FlowNodeDsl node) {
        ServiceTask service = new ServiceTask();
        applyId(service, node);
        // Single shared bean (registered in M2) handles every NOTIFY node; the chart node key
        // is read from the BPMN element id at runtime to look up the recipients.
        service.setImplementationType("delegateExpression");
        service.setImplementation("${flowNotifyHandler}");
        service.addAttribute(buildAttribute("nodeKey", node.getKey()));
        return service;
    }

    private ServiceTask emitScript(FlowNodeDsl node) {
        ServiceTask service = new ServiceTask();
        applyId(service, node);
        Object handler = node.getProperties() != null ? node.getProperties().get("handlerName") : null;
        service.setImplementationType("delegateExpression");
        service.setImplementation("${" + Objects.requireNonNull(handler, "handlerName required") + "}");
        service.addAttribute(buildAttribute("nodeKey", node.getKey()));
        return service;
    }

    private ExclusiveGateway emitExclusive(FlowNodeDsl node) {
        ExclusiveGateway gateway = new ExclusiveGateway();
        applyId(gateway, node);
        return gateway;
    }

    private ParallelGateway emitParallel(FlowNodeDsl node) {
        ParallelGateway gateway = new ParallelGateway();
        applyId(gateway, node);
        return gateway;
    }

    private ParallelGateway emitJoint(FlowNodeDsl node) {
        ParallelGateway gateway = new ParallelGateway();
        applyId(gateway, node);
        return gateway;
    }

    private SequenceFlow toSequenceFlow(FlowEdgeDsl edge) {
        SequenceFlow flow = new SequenceFlow();
        flow.setId(edge.getKey() != null ? edge.getKey()
                : "edge-" + UUID.randomUUID().toString().substring(0, 8));
        flow.setSourceRef(edge.getFrom());
        flow.setTargetRef(edge.getTo());
        if (edge.getCondition() != null && !edge.getCondition().isBlank()) {
            flow.setConditionExpression(edge.getCondition());
        }
        if (edge.getLabel() != null) {
            flow.setName(edge.getLabel());
        }
        return flow;
    }

    private void applyId(FlowElement element, FlowNodeDsl node) {
        element.setId(node.getKey());
        if (node.getLabel() != null) {
            element.setName(node.getLabel());
        }
    }

    private org.flowable.bpmn.model.ExtensionAttribute buildAttribute(String name, String value) {
        org.flowable.bpmn.model.ExtensionAttribute attr = new org.flowable.bpmn.model.ExtensionAttribute(name);
        attr.setNamespace(SMART_NS);
        attr.setNamespacePrefix("smart");
        attr.setValue(value);
        return attr;
    }

    private String buildCompletionCondition(Map<String, Object> props) {
        String passRule = String.valueOf(props.getOrDefault("passRule", "all"));
        return switch (passRule) {
            case "any" -> "${nrOfCompletedInstances >= 1}";
            case "ratio" -> {
                Object ratio = props.getOrDefault("passRatio", 0.5);
                yield "${nrOfCompletedInstances/nrOfInstances >= " + ratio + "}";
            }
            // "all" or anything else
            default -> "${nrOfCompletedInstances == nrOfInstances}";
        };
    }
}
