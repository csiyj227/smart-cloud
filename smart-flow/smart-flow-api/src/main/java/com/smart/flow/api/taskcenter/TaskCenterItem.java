package com.smart.flow.api.taskcenter;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Single row rendered by the task-center listing UI.
 *
 * <p>Field set kept intentionally narrow - everything heavier (form payload, approval
 * timeline) is fetched lazily by the detail page so the listing endpoint stays fast even
 * with hundreds of rows.
 */
@Value
@Builder
public class TaskCenterItem {

    String taskId;
    String processInstanceId;
    String chartKey;
    String chartName;
    String bizNo;
    String title;
    String nodeKey;
    String nodeName;
    String viewStatus;
    Long starterId;
    String starterName;
    LocalDateTime receivedAt;
    LocalDateTime finishedAt;
    Long formId;
}
