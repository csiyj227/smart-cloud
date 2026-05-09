package com.smart.flow.infrastructure.flowable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.flow.domain.assignee.AssigneeContext;
import com.smart.flow.domain.assignee.AssigneeResolution;
import com.smart.flow.domain.assignee.AssigneeResolver;
import com.smart.flow.domain.assignee.AssigneeResolverRegistry;
import com.smart.flow.domain.instance.event.TaskAssignedEvent;
import com.smart.flow.infrastructure.config.FlowJacksonConfig;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring-managed {@link TaskListener} that BPMN user tasks reference by name to compute their
 * own assignees at runtime.
 *
 * <p>Wired into BPMN via a Flowable {@code <flowable:taskListener event="create" delegateExpression="${assigneeRoutingTaskListener}"/>}
 * element emitted by {@link com.smart.flow.infrastructure.compiler.BpmnEmitter} on every
 * APPROVE node. The expression resolves through Flowable's {@code SpringExpressionManager}
 * (configured automatically by the spring-boot starter), which means we can rely on real
 * Spring DI - no {@code SpringUtil.getBean()} reflection hacks like in the reference
 * implementations.
 *
 * <p>The strategy key and resolver parameters are pushed into this listener via Flowable
 * {@link Expression} field injection: every UserTask declares
 * {@code <flowable:field name="strategy">} and {@code <flowable:field name="paramsJson">}
 * (see {@link com.smart.flow.infrastructure.compiler.BpmnEmitter#FIELD_STRATEGY} /
 * {@link com.smart.flow.infrastructure.compiler.BpmnEmitter#FIELD_PARAMS_JSON}), and Flowable
 * binds them to the {@link #strategy} / {@link #paramsJson} fields below at parse time. We
 * deliberately avoid stashing the strategy into process variables because that would (a) leak
 * implementation noise into history tables and (b) make per-node overrides on parallel branches
 * fight over a single variable name.
 *
 * <p>Resolution outcomes:
 * <ul>
 *   <li>single user -> {@code task.setAssignee(userId)} - the standard Flowable assignee path;</li>
 *   <li>multiple users -> {@code task.addCandidateUser(userId)} for each, leaving
 *       {@code assignee} null until someone claims the task;</li>
 *   <li>empty -> task is left unassigned. The instance still progresses (Flowable does not
 *       auto-fail on empty assignment), but the task center will mark it as orphaned and a
 *       supervisor must reassign manually. We chose this over hard-failing the instance to
 *       give operations a chance to recover from data drift (e.g. a role was deleted).</li>
 * </ul>
 */
@Slf4j
@Component("assigneeRoutingTaskListener")
public class AssigneeRoutingTaskListener implements TaskListener {

    private static final TypeReference<Map<String, Object>> PARAMS_TYPE = new TypeReference<>() {
    };

    private final AssigneeResolverRegistry registry;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Injected by Flowable from the {@code <flowable:field name="strategy">} entry the emitter
     * writes onto each user task. Field injection happens once per UserTask parse, so the same
     * {@link Expression} instance is reused across every task instance of that node - no
     * surprises with state, the listener is still effectively stateless per invocation.
     */
    private Expression strategy;

    /** Mirrors {@code <flowable:field name="paramsJson">}. May be null when no params declared. */
    private Expression paramsJson;

    public AssigneeRoutingTaskListener(AssigneeResolverRegistry registry,
                                       ApplicationEventPublisher eventPublisher,
                                       @Qualifier(FlowJacksonConfig.BEAN_NAME) ObjectMapper objectMapper) {
        this.registry = registry;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void notify(DelegateTask delegateTask) {
        String strategyKey = readField(strategy, delegateTask);
        if (strategyKey == null || strategyKey.isBlank()) {
            log.debug("Task {} has no assignee strategy declared; leaving Flowable's default behaviour",
                    delegateTask.getId());
            return;
        }
        Map<String, Object> params = decodeParams(readField(paramsJson, delegateTask));

        AssigneeContext context = AssigneeContext.builder()
                .processInstanceId(delegateTask.getProcessInstanceId())
                .nodeKey(delegateTask.getTaskDefinitionKey())
                .tenantId(parseLong(delegateTask.getVariable(FlowVariables.TENANT_ID)))
                .starterUserId(parseLong(delegateTask.getVariable(FlowVariables.STARTER_ID)))
                .previousActorUserIds(readPreviousActors(delegateTask))
                .parameters(params)
                .formVariables((Map<String, Object>) delegateTask.getVariable(FlowVariables.FORM_DATA))
                .build();

        AssigneeResolver resolver = registry.require(strategyKey);
        AssigneeResolution resolution = resolver.resolve(context);

        if (resolution.isEmpty()) {
            log.warn("Resolver '{}' returned no candidates for task {} (node={}); task left unassigned for ops to fix",
                    strategyKey, delegateTask.getId(), delegateTask.getTaskDefinitionKey());
            return;
        }
        List<Long> userIds = resolution.getUserIds();
        if (userIds.size() == 1) {
            delegateTask.setAssignee(userIds.get(0).toString());
        } else {
            userIds.forEach(id -> delegateTask.addCandidateUser(id.toString()));
        }
        log.debug("Routed task {} via strategy '{}' to {}", delegateTask.getId(), strategyKey, userIds);

        // Notify the projector so the CQRS read model gets a row per candidate. The event is
        // dispatched synchronously inside the engine's transaction; the projector itself uses
        // REQUIRES_NEW to commit independently so a projector failure cannot poison the engine.
        eventPublisher.publishEvent(new TaskAssignedEvent(
                delegateTask.getId(),
                delegateTask.getProcessInstanceId(),
                delegateTask.getTaskDefinitionKey(),
                delegateTask.getName(),
                userIds));
    }

    private List<Long> readPreviousActors(DelegateTask task) {
        Object raw = task.getVariable(FlowVariables.PREVIOUS_ACTORS);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(o -> parseLong(o)).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        }
        return Arrays.stream(raw.toString().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseLong)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readField(Expression field, DelegateTask task) {
        if (field == null) {
            return null;
        }
        Object value = field.getValue(task);
        return value == null ? null : value.toString();
    }

    private Map<String, Object> decodeParams(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, PARAMS_TYPE);
            return map == null ? Collections.emptyMap() : map;
        } catch (JsonProcessingException e) {
            // Misconfigured BPMN -> we'd rather degrade to "no params" and let the resolver
            // handle absence than fail the entire task. The warning surfaces the issue in ops.
            log.warn("Failed to decode assignee paramsJson '{}': {}", json, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
