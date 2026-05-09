package com.smart.flow.domain.form;

import com.smart.flow.infrastructure.persistence.entity.FlowFormSnapshotEntity;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the append-only {@code flow_form_snapshot} table. Snapshots are
 * the audit trail's source of truth for "what did each approver see when they acted".
 */
public interface FormSnapshotRepository {

    /** Append a new snapshot row. Always inserts; never updates. */
    void append(FlowFormSnapshotEntity snapshot);

    /** All snapshots of an instance, ordered by capture time ascending (timeline). */
    List<FlowFormSnapshotEntity> listByProcessInstance(String processInstanceId);

    /** The single most recent snapshot for an instance, used as the live view payload. */
    Optional<FlowFormSnapshotEntity> findLatest(String processInstanceId);
}
