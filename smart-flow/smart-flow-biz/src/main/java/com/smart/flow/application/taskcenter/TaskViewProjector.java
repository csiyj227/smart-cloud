package com.smart.flow.application.taskcenter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smart.flow.api.instance.FlowInstanceState;
import com.smart.flow.domain.definition.FlowDefinitionRepository;
import com.smart.flow.domain.instance.FlowInstanceRepository;
import com.smart.flow.domain.instance.event.InstanceTerminatedEvent;
import com.smart.flow.domain.instance.event.TaskAssignedEvent;
import com.smart.flow.domain.instance.event.TaskCompletedEvent;
import com.smart.flow.infrastructure.cache.PendingCountCache;
import com.smart.flow.infrastructure.persistence.entity.FlowInstanceBizEntity;
import com.smart.flow.infrastructure.persistence.entity.FlowTaskViewEntity;
import com.smart.flow.infrastructure.persistence.mapper.FlowTaskViewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Translates domain events into mutations against the {@code flow_task_view} CQRS read model.
 *
 * <p>The projector is the <em>only</em> writer of {@code flow_task_view}; query services are
 * read-only. This split is what lets the badge endpoint answer in a single index-backed
 * SELECT instead of joining the {@code act_*} tables every poll.
 *
 * <h3>Why every listener uses {@link TransactionPhase#AFTER_COMMIT}</h3>
 * Even {@code TaskAssignedEvent} fires from inside the engine's transaction (Flowable's
 * task-listener runs as part of {@code taskService.complete} / {@code start}, both of
 * which are wrapped in {@code @Transactional} on the calling application service). If we
 * projected eagerly with a {@link Propagation#REQUIRES_NEW} transaction and the outer
 * transaction later rolled back, we would leave orphan rows in {@code flow_task_view}
 * (a phantom pending task pointing at a Flowable task that no longer exists). Waiting
 * for {@code AFTER_COMMIT} guarantees we only ever materialise rows that the engine has
 * also durably committed; the trade-off is that projection happens marginally later, but
 * the badge cache eviction in the same handler closes the visibility gap quickly.
 *
 * <p>{@code fallbackExecution = true} is set so that if a caller (e.g. a test or a
 * direct engine boot) emits an event without an active transaction, the listener still
 * runs synchronously rather than being silently dropped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskViewProjector {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CLAIMED = "claimed";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_WITHDRAWN = "withdrawn";
    private static final String STATUS_TERMINATED = "terminated";

    private final FlowTaskViewMapper viewMapper;
    private final FlowInstanceRepository instanceRepository;
    private final FlowDefinitionRepository definitionRepository;
    private final PendingCountCache pendingCountCache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onTaskAssigned(TaskAssignedEvent event) {
        FlowInstanceBizEntity biz = instanceRepository.findByProcessInstanceId(event.getProcessInstanceId())
                .orElse(null);
        if (biz == null) {
            // The instance row may not exist yet for engine-only smoke tests - this is the
            // single place we tolerate that, because skipping projection here is harmless.
            log.debug("Skipping projection for task {} - no flow_instance_biz row yet", event.getTaskId());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long candidateUserId : event.getCandidateUserIds()) {
            FlowTaskViewEntity row = new FlowTaskViewEntity();
            row.setTaskId(event.getTaskId());
            row.setProcessInstanceId(event.getProcessInstanceId());
            row.setChartKey(biz.getChartKey());
            row.setChartName(definitionRepository.findById(biz.getChartId())
                    .map(def -> def.getChartName()).orElse(biz.getChartKey()));
            row.setBizNo(biz.getBizNo());
            row.setTitle(biz.getTitle());
            row.setNodeKey(event.getNodeKey());
            row.setNodeName(event.getNodeName());
            row.setCandidateUserId(candidateUserId);
            row.setStarterId(biz.getStarterId());
            row.setStarterName(biz.getStarterName());
            row.setViewStatus(STATUS_PENDING);
            row.setReceivedAt(now);
            viewMapper.insert(row);
            pendingCountCache.evict(candidateUserId);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onTaskCompleted(TaskCompletedEvent event) {
        Set<Long> evictUsers = collectCandidateUserIds(event.getTaskId());
        viewMapper.update(null, new LambdaUpdateWrapper<FlowTaskViewEntity>()
                .eq(FlowTaskViewEntity::getTaskId, event.getTaskId())
                .set(FlowTaskViewEntity::getViewStatus, STATUS_COMPLETED)
                .set(FlowTaskViewEntity::getFinishedAt, LocalDateTime.now()));
        evictUsers.forEach(pendingCountCache::evict);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onInstanceTerminated(InstanceTerminatedEvent event) {
        String terminalStatus = event.getTerminalState() == FlowInstanceState.WITHDRAWN
                ? STATUS_WITHDRAWN
                : STATUS_TERMINATED;
        Set<Long> evictUsers = collectPendingCandidateUserIds(event.getProcessInstanceId());
        viewMapper.update(null, new LambdaUpdateWrapper<FlowTaskViewEntity>()
                .eq(FlowTaskViewEntity::getProcessInstanceId, event.getProcessInstanceId())
                .in(FlowTaskViewEntity::getViewStatus, STATUS_PENDING, STATUS_CLAIMED)
                .set(FlowTaskViewEntity::getViewStatus, terminalStatus)
                .set(FlowTaskViewEntity::getFinishedAt, LocalDateTime.now()));
        evictUsers.forEach(pendingCountCache::evict);
    }

    private Set<Long> collectCandidateUserIds(String taskId) {
        List<FlowTaskViewEntity> rows = viewMapper.selectList(new LambdaQueryWrapper<FlowTaskViewEntity>()
                .eq(FlowTaskViewEntity::getTaskId, taskId));
        Set<Long> result = new HashSet<>();
        for (FlowTaskViewEntity row : rows) {
            if (row.getCandidateUserId() != null) {
                result.add(row.getCandidateUserId());
            }
        }
        return result;
    }

    private Set<Long> collectPendingCandidateUserIds(String processInstanceId) {
        List<FlowTaskViewEntity> rows = viewMapper.selectList(new LambdaQueryWrapper<FlowTaskViewEntity>()
                .eq(FlowTaskViewEntity::getProcessInstanceId, processInstanceId)
                .in(FlowTaskViewEntity::getViewStatus, STATUS_PENDING, STATUS_CLAIMED));
        Set<Long> result = new HashSet<>();
        for (FlowTaskViewEntity row : rows) {
            if (row.getCandidateUserId() != null) {
                result.add(row.getCandidateUserId());
            }
        }
        return result;
    }
}
