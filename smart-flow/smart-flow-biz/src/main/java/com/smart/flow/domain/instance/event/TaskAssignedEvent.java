package com.smart.flow.domain.instance.event;

import lombok.Value;

import java.util.List;

/**
 * Fired by {@code AssigneeRoutingTaskListener} immediately after Flowable's "create" event
 * has resolved a task's candidate user(s). The {@code TaskViewProjector} subscribes and
 * materialises one row per candidate in {@code flow_task_view}.
 *
 * <p>{@code candidateUserIds} carries the resolved list verbatim (single-assignee case has a
 * 1-element list). Keeping the projector deterministic this way avoids the projector having
 * to re-query the engine, which is both faster and free of consistency races.
 */
@Value
public class TaskAssignedEvent {

    String taskId;
    String processInstanceId;
    String nodeKey;
    String nodeName;
    List<Long> candidateUserIds;
}
