package com.smart.flow.application.form;

import com.smart.flow.domain.form.FormSnapshotRepository;
import com.smart.flow.infrastructure.persistence.entity.FlowFormSnapshotEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Append-only snapshot writer / reader. Keeps the {@code flow_form_snapshot} write path
 * out of {@link com.smart.flow.application.instance.FlowInstanceAppService} so the
 * instance service stays focused on engine orchestration.
 *
 * <p>Snapshot type codes:
 * <ul>
 *   <li>{@code "0"} - starter snapshot (taken at instance start)</li>
 *   <li>{@code "1"} - approval snapshot (taken at task complete)</li>
 *   <li>{@code "2"} - sign-off snapshot (reserved for addSign-style flows)</li>
 *   <li>{@code "3"} - system patch (reserved for admin "fix the data" actions)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FormSnapshotAppService {

    public static final String TYPE_STARTER = "0";
    public static final String TYPE_APPROVAL = "1";
    public static final String TYPE_SIGN = "2";
    public static final String TYPE_PATCH = "3";

    private final FormSnapshotRepository snapshotRepository;
    private final FormBindingAppService bindingService;

    /**
     * Persists a snapshot at instance start. Returns the new snapshot id so the caller
     * can write it to {@code flow_instance_biz.latest_snapshot_id}.
     */
    @Transactional(rollbackFor = Exception.class)
    public Long captureStarterSnapshot(String processInstanceId, Long formId,
                                       Map<String, Object> payload, Long capturedBy) {
        return capture(processInstanceId, null, null, formId, TYPE_STARTER, payload, capturedBy);
    }

    /**
     * Persists a snapshot taken when an approver completes a task.
     */
    @Transactional(rollbackFor = Exception.class)
    public Long captureApprovalSnapshot(String processInstanceId, String taskId, String nodeKey,
                                        Long formId, Map<String, Object> payload, Long capturedBy) {
        return capture(processInstanceId, taskId, nodeKey, formId, TYPE_APPROVAL, payload, capturedBy);
    }

    public List<FlowFormSnapshotEntity> listTimeline(String processInstanceId) {
        return snapshotRepository.listByProcessInstance(processInstanceId);
    }

    /**
     * Returns the live form payload as a typed map (the most-recent snapshot's payload
     * is treated as the source of truth for "what does the form currently look like").
     * Returns an empty map when no snapshot exists yet, never {@code null}.
     */
    public Map<String, Object> currentPayload(String processInstanceId) {
        Optional<FlowFormSnapshotEntity> latest = snapshotRepository.findLatest(processInstanceId);
        if (latest.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return bindingService.decodeFormPayload(latest.get().getPayload());
    }

    /**
     * Returns the latest form snapshot payload as a Map for the given process instance.
     * Used by the instance detail view to display the current form data.
     */
    public Map<String, Object> getLatestSnapshot(String processInstanceId) {
        Optional<FlowFormSnapshotEntity> latest = snapshotRepository.findLatest(processInstanceId);
        if (latest.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return bindingService.decodeFormPayload(latest.get().getPayload());
    }

    /* ============================================================ helpers ===== */

    private Long capture(String processInstanceId, String taskId, String nodeKey,
                         Long formId, String snapshotType,
                         Map<String, Object> payload, Long capturedBy) {
        FlowFormSnapshotEntity entity = new FlowFormSnapshotEntity();
        entity.setProcessInstanceId(processInstanceId);
        entity.setTaskId(taskId);
        entity.setNodeKey(nodeKey);
        entity.setFormId(formId);
        entity.setSnapshotType(snapshotType);
        entity.setPayload(bindingService.encodeFormPayload(payload));
        entity.setCapturedBy(capturedBy);
        snapshotRepository.append(entity);
        return entity.getSnapshotId();
    }
}
