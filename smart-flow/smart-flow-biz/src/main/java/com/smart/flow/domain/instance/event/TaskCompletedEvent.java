package com.smart.flow.domain.instance.event;

import lombok.Value;

/**
 * Fired by {@code FlowInstanceAppService.complete()} after Flowable has accepted the task
 * completion. The {@code TaskViewProjector} listens to flip every {@code flow_task_view} row
 * sharing this {@code taskId} from {@code pending}/{@code claimed} to {@code completed}.
 */
@Value
public class TaskCompletedEvent {

    String taskId;
    String processInstanceId;
    Long actorId;
}
