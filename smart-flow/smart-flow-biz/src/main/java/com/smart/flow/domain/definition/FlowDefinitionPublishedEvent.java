package com.smart.flow.domain.definition;

import lombok.Value;

/**
 * Domain event fired the moment a flow definition transitions from DRAFT to PUBLISHED and the
 * resulting BPMN has been successfully deployed to Flowable.
 *
 * <p>Downstream concerns (cache invalidation, designer notifications, audit log, search
 * indexing) subscribe via Spring's {@code ApplicationEventPublisher} mechanism. We chose the
 * built-in publisher rather than dragging in a message bus because:
 * <ul>
 *   <li>publish-on-commit semantics are cheap to provide via
 *       {@code @TransactionalEventListener(phase = AFTER_COMMIT)};</li>
 *   <li>the producer (the application service) and the consumers all live in the same JVM at
 *       this point - introducing a queue would be premature.</li>
 * </ul>
 *
 * <p>Field naming uses the {@code chart} prefix (not {@code processDefinition}) deliberately:
 * the smart-flow vocabulary distinguishes the user-facing "chart" (DSL view) from the
 * Flowable-internal "process definition" (BPMN view), and consumers should know they are
 * looking at the chart side.
 */
@Value
public class FlowDefinitionPublishedEvent {

    Long chartId;
    String chartKey;
    Integer chartVersion;
    String deploymentId;
    String processDefinitionId;
    Long publisherUserId;
}
