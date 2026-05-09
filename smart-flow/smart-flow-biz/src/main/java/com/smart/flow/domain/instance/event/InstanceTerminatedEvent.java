package com.smart.flow.domain.instance.event;

import com.smart.flow.api.instance.FlowInstanceState;
import lombok.Value;

/**
 * Fired whenever an instance reaches a terminal state ({@code WITHDRAWN} or
 * {@code TERMINATED}) - the projector cancels every still-open view row that belongs to it.
 * Successful completion is covered by per-task {@link TaskCompletedEvent}s instead, so the
 * projector never has to scan history to figure out what happened.
 */
@Value
public class InstanceTerminatedEvent {

    String processInstanceId;
    FlowInstanceState terminalState;
    Long actorId;
}
