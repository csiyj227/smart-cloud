package com.smart.flow.domain.audit;

import com.smart.flow.infrastructure.persistence.entity.FlowApprovalRecordEntity;

import java.util.List;

/**
 * Append-only port for the approval audit log. The interface deliberately exposes only
 * write semantics here - read paths go through dedicated query objects in the task-center
 * read model so that the audit log itself can be sharded / archived without breaking
 * downstream code.
 */
public interface ApprovalRecordRepository {

    void append(FlowApprovalRecordEntity entity);

    List<FlowApprovalRecordEntity> findByProcessInstanceId(String processInstanceId);
}
