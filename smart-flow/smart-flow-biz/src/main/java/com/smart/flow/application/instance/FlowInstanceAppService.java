package com.smart.flow.application.instance;

import com.smart.common.security.component.PermissionEvaluator;
import com.smart.flow.api.dsl.ApprovalAction;
import com.smart.flow.api.form.FieldRuleSpec;
import com.smart.flow.api.instance.ApprovalRecordVO;
import com.smart.flow.api.instance.CompleteTaskCmd;
import com.smart.flow.api.instance.FlowInstanceDetailVO;
import com.smart.flow.api.instance.FlowInstanceState;
import com.smart.flow.api.instance.StartFlowCmd;
import com.smart.flow.api.instance.TransferTaskCmd;
import com.smart.flow.application.form.FormBindingAppService;
import com.smart.flow.application.form.FormSnapshotAppService;
import com.smart.flow.domain.audit.ApprovalRecordRepository;
import com.smart.flow.domain.audit.DelegationRepository;
import com.smart.flow.domain.definition.FlowDefinitionRepository;
import com.smart.flow.domain.definition.FlowDefinitionStatus;
import com.smart.flow.domain.form.FieldPermissionEnforcer;
import com.smart.flow.domain.instance.BizNoGenerator;
import com.smart.flow.domain.instance.FlowInstanceRepository;
import com.smart.flow.domain.instance.event.InstanceTerminatedEvent;
import com.smart.flow.domain.instance.event.TaskCompletedEvent;
import com.smart.flow.infrastructure.flowable.FlowVariables;
import com.smart.flow.infrastructure.persistence.entity.FlowApprovalRecordEntity;
import com.smart.flow.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.smart.flow.infrastructure.persistence.entity.FlowDelegationEntity;
import com.smart.flow.infrastructure.persistence.entity.FlowInstanceBizEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application service that drives every <em>runtime</em> action against a flow instance.
 *
 * <p>Method scope summary:
 * <table>
 *   <tr><th>operation</th><th>preconditions</th><th>effects</th></tr>
 *   <tr><td>start</td><td>chart published</td>
 *       <td>creates {@code flow_instance_biz} + Flowable instance + first approval record</td></tr>
 *   <tr><td>complete (approve/reject)</td><td>task assigned to caller</td>
 *       <td>completes Flowable task, appends audit record, updates biz status when instance ends</td></tr>
 *   <tr><td>transfer / delegate</td><td>task active and owned by caller</td>
 *       <td>switches assignee on Flowable task, appends both an audit and a delegation row</td></tr>
 *   <tr><td>withdraw</td><td>caller is starter, instance still on first reachable user task</td>
 *       <td>terminates the Flowable instance with a withdrawal reason, flips biz status</td></tr>
 *   <tr><td>suspend / resume</td><td>caller has admin permission (enforced upstream)</td>
 *       <td>delegates to {@code RuntimeService}; biz row is untouched (status remains 'running')</td></tr>
 *   <tr><td>terminate</td><td>caller has admin permission</td>
 *       <td>deletes the Flowable instance, flips biz status to 'terminated'</td></tr>
 * </table>
 *
 * <p><strong>Why a single application service rather than two?</strong> The reference
 * flow split definition and instance into separate microservices, which forced a
 * Feign-driven RPC for every "complete a task and check the chart" combo. Smart-flow keeps
 * everything in {@code smart-flow}, but inside the service these concerns are split into
 * clearly-named private helpers so the file remains readable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInstanceAppService {

    private final FlowDefinitionRepository definitionRepository;
    private final FlowInstanceRepository instanceRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final DelegationRepository delegationRepository;
    private final BizNoGenerator bizNoGenerator;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final PermissionEvaluator permissionEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final FormBindingAppService formBindingService;
    private final FormSnapshotAppService formSnapshotService;
    private final FieldPermissionEnforcer fieldPermissionEnforcer;
    private final com.smart.flow.domain.form.FormQueryPort formQueryPort;

    /* ====================================================================== start ===== */

    @Transactional(rollbackFor = Exception.class)
    public String start(StartFlowCmd cmd) {
        FlowDefinitionEntity definition = resolveRunnableDefinition(cmd);
        Long starterId = requireCurrentUserId();
        Long tenantId = permissionEvaluator.getCurrentTenantId();

        Map<String, Object> variables = buildStartVariables(definition, cmd, starterId, tenantId);
        ProcessInstance processInstance = runtimeService.createProcessInstanceBuilder()
                .processDefinitionId(definition.getProcessDefinitionId())
                .variables(variables)
                .start();

        String bizNo = bizNoGenerator.next(definition.getChartKey(), cmd.getBizNoPrefix());
        FlowInstanceBizEntity biz = new FlowInstanceBizEntity();
        biz.setProcessInstanceId(processInstance.getProcessInstanceId());
        biz.setChartId(definition.getChartId());
        biz.setChartKey(definition.getChartKey());
        biz.setChartVersion(definition.getChartVersion());
        biz.setBizNo(bizNo);
        biz.setTitle(cmd.getTitle());
        biz.setStarterId(starterId);
        biz.setStarterName(permissionEvaluator.getCurrentUserName());
        biz.setBizStatus(FlowInstanceState.RUNNING.getCode());
        biz.setStartTime(LocalDateTime.now());

        // Capture the starter form payload before persisting the biz row so latest_snapshot_id
        // can be denormalised in a single insert and downstream code can rely on the link
        // existing immediately.
        Long snapshotId = captureStarterSnapshotIfBound(
                processInstance.getProcessInstanceId(), definition.getBoundFormId(),
                cmd.getFormData(), starterId);
        biz.setLatestSnapshotId(snapshotId);
        instanceRepository.save(biz);

        appendRecord(processInstance.getProcessInstanceId(), null, null, null,
                ApprovalAction.SUBMIT, starterId, null, null, null);

        log.info("Started instance {} (chart={} v{}, bizNo={})",
                processInstance.getProcessInstanceId(), definition.getChartKey(),
                definition.getChartVersion(), bizNo);
        return processInstance.getProcessInstanceId();
    }

    /* ===================================================================== complete ===== */

    @Transactional(rollbackFor = Exception.class)
    public void complete(CompleteTaskCmd cmd) {
        Task task = requireActiveTask(cmd.getTaskId());
        Long actorId = requireCurrentUserId();
        ensureTaskOwnedBy(task, actorId);

        // Field-level permission check: any rule violation aborts the whole transaction
        // before we touch the engine, leaving Flowable's state untouched.
        Long chartId = currentChartId(task.getProcessInstanceId());
        Long boundFormId = currentBoundFormId(task.getProcessInstanceId());
        enforceFieldRules(chartId, task.getTaskDefinitionKey(),
                cmd.getFormData(), task.getProcessInstanceId());

        Map<String, Object> variables = new HashMap<>();
        if (cmd.getFormData() != null) {
            variables.put(FlowVariables.FORM_DATA, cmd.getFormData());
        }
        // Append the actor to the previous-actor chain so downstream "leader of previous"
        // resolvers see the right reference user.
        appendPreviousActor(task.getProcessInstanceId(), actorId);

        switch (cmd.getAction()) {
            case APPROVE -> taskService.complete(task.getId(), variables);
            case REJECT -> rejectTask(task, variables);
            default -> throw new IllegalArgumentException(
                    "complete() only supports APPROVE / REJECT, got " + cmd.getAction());
        }

        appendRecord(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                task.getName(), cmd.getAction(), actorId, null, null, cmd.getComment());

        // Snapshot the form as it stood when this approver acted, so the audit timeline can
        // replay each step's payload independently. Only persists when the chart is bound to
        // a form and the caller actually submitted data.
        captureApprovalSnapshotIfBound(task.getProcessInstanceId(), task.getId(),
                task.getTaskDefinitionKey(), boundFormId, cmd.getFormData(), actorId);

        // Tell the projector the task has finished so it can flip every sibling view row to
        // 'completed' and evict the affected users' badge cache.
        eventPublisher.publishEvent(new TaskCompletedEvent(task.getId(), task.getProcessInstanceId(), actorId));

        // If the instance has finished, reflect it on the biz row.
        ProcessInstance live = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        if (live == null) {
            FlowInstanceState terminal = cmd.getAction() == ApprovalAction.REJECT
                    ? FlowInstanceState.REJECTED
                    : FlowInstanceState.APPROVED;
            finaliseBizRow(task.getProcessInstanceId(), terminal);
            // Notify custom form business table to sync status
            notifyCustomFormStatusChange(task.getProcessInstanceId(), terminal);
            // A reject ends the instance abruptly - notify the projector so any other open
            // view rows on the same instance are cancelled too.
            if (cmd.getAction() == ApprovalAction.REJECT) {
                eventPublisher.publishEvent(new InstanceTerminatedEvent(
                        task.getProcessInstanceId(), terminal, actorId));
            }
        }
    }

    /* ============================================================ transfer / delegate ===== */

    @Transactional(rollbackFor = Exception.class)
    public void transfer(TransferTaskCmd cmd) {
        Task task = requireActiveTask(cmd.getTaskId());
        Long actorId = requireCurrentUserId();
        ensureTaskOwnedBy(task, actorId);

        if (cmd.isTemporary()) {
            // Flowable's "delegate" flag means the task returns to the original owner upon
            // completion; the engine handles the bookkeeping for us.
            taskService.delegateTask(task.getId(), cmd.getToUserId().toString());
        } else {
            taskService.setAssignee(task.getId(), cmd.getToUserId().toString());
        }

        FlowDelegationEntity delegation = new FlowDelegationEntity();
        delegation.setTaskId(task.getId());
        delegation.setProcessInstanceId(task.getProcessInstanceId());
        delegation.setDelegationType(cmd.isTemporary() ? "delegate" : "transfer");
        delegation.setFromUserId(actorId);
        delegation.setToUserId(cmd.getToUserId());
        delegation.setReason(cmd.getReason());
        delegationRepository.append(delegation);

        appendRecord(task.getProcessInstanceId(), task.getId(), task.getTaskDefinitionKey(),
                task.getName(),
                cmd.isTemporary() ? ApprovalAction.DELEGATE : ApprovalAction.TRANSFER,
                actorId, cmd.getToUserId(), null, cmd.getReason());
    }

    /* ===================================================================== withdraw ===== */

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(String processInstanceId, String comment) {
        FlowInstanceBizEntity biz = instanceRepository.findByProcessInstanceId(processInstanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No biz row for processInstanceId=" + processInstanceId));
        Long actorId = requireCurrentUserId();
        if (!actorId.equals(biz.getStarterId())) {
            throw new IllegalStateException(
                    "Only the starter can withdraw an instance (starter=" + biz.getStarterId()
                            + ", caller=" + actorId + ")");
        }
        // Withdrawal model: terminate the running instance with a special reason. The audit
        // record carries the rich semantics; Flowable history simply notes "deleted".
        runtimeService.deleteProcessInstance(processInstanceId, "withdrawn-by-starter:" + actorId);
        finaliseBizRow(processInstanceId, FlowInstanceState.WITHDRAWN);
        appendRecord(processInstanceId, null, null, null,
                ApprovalAction.WITHDRAW, actorId, null, null, comment);
        eventPublisher.publishEvent(new InstanceTerminatedEvent(
                processInstanceId, FlowInstanceState.WITHDRAWN, actorId));
    }

    /* =========================================================== suspend / resume ===== */

    @Transactional(rollbackFor = Exception.class)
    public void suspend(String processInstanceId) {
        runtimeService.suspendProcessInstanceById(processInstanceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resume(String processInstanceId) {
        runtimeService.activateProcessInstanceById(processInstanceId);
    }

    /* ==================================================================== terminate ===== */

    @Transactional(rollbackFor = Exception.class)
    public void terminate(String processInstanceId, String reason) {
        Long actorId = requireCurrentUserId();
        runtimeService.deleteProcessInstance(processInstanceId, "terminated:" + reason);
        finaliseBizRow(processInstanceId, FlowInstanceState.TERMINATED);
        appendRecord(processInstanceId, null, null, null,
                ApprovalAction.TERMINATE, actorId, null, null, reason);
        eventPublisher.publishEvent(new InstanceTerminatedEvent(
                processInstanceId, FlowInstanceState.TERMINATED, actorId));
    }

    /* ===================================================================== helpers ===== */

    private FlowDefinitionEntity resolveRunnableDefinition(StartFlowCmd cmd) {
        FlowDefinitionEntity definition = (cmd.getChartVersion() == null)
                ? definitionRepository.findLatestPublished(cmd.getChartKey())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No published version for chartKey=" + cmd.getChartKey()))
                : definitionRepository.findByKeyAndVersion(cmd.getChartKey(), cmd.getChartVersion())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Chart not found: " + cmd.getChartKey() + " v" + cmd.getChartVersion()));
        if (!FlowDefinitionStatus.PUBLISHED.getCode().equals(definition.getPublishStatus())) {
            throw new IllegalStateException(
                    "Chart " + cmd.getChartKey() + " v" + definition.getChartVersion()
                            + " is in status " + definition.getPublishStatus() + " and cannot start instances");
        }
        if (definition.getProcessDefinitionId() == null) {
            throw new IllegalStateException(
                    "Chart " + cmd.getChartKey() + " v" + definition.getChartVersion()
                            + " has no Flowable processDefinitionId; was the publish completed?");
        }
        return definition;
    }

    private Map<String, Object> buildStartVariables(FlowDefinitionEntity definition, StartFlowCmd cmd,
                                                    Long starterId, Long tenantId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(FlowVariables.STARTER_ID, starterId);
        variables.put(FlowVariables.CHART_KEY, definition.getChartKey());
        variables.put(FlowVariables.CHART_VERSION, definition.getChartVersion());
        if (tenantId != null) {
            variables.put(FlowVariables.TENANT_ID, tenantId);
        }
        if (cmd.getFormData() != null) {
            variables.put(FlowVariables.FORM_DATA, cmd.getFormData());
        }
        // Two extra variables let listeners (TaskListener / DelegateExecution) reach the
        // form metadata without re-querying flow_definition every step.
        variables.put(FlowVariables.CHART_ID, definition.getChartId());
        if (definition.getBoundFormId() != null) {
            variables.put(FlowVariables.BOUND_FORM_ID, definition.getBoundFormId());
        }
        return variables;
    }

    /* ============================================================ form helpers ===== */

    private Long captureStarterSnapshotIfBound(String processInstanceId, Long boundFormId,
                                               Map<String, Object> formData, Long starterId) {
        if (boundFormId == null || formData == null || formData.isEmpty()) {
            return null;
        }
        Long snapshotId = formSnapshotService.captureStarterSnapshot(
                processInstanceId, boundFormId, formData, starterId);
        // Mirror the snapshot id onto an engine variable so listeners can chain off it
        // without going to the database.
        runtimeService.setVariable(processInstanceId, FlowVariables.LATEST_SNAPSHOT_ID, snapshotId);
        return snapshotId;
    }

    private void captureApprovalSnapshotIfBound(String processInstanceId, String taskId, String nodeKey,
                                                Long boundFormId, Map<String, Object> formData, Long actorId) {
        if (boundFormId == null || formData == null || formData.isEmpty()) {
            return;
        }
        Long snapshotId = formSnapshotService.captureApprovalSnapshot(
                processInstanceId, taskId, nodeKey, boundFormId, formData, actorId);
        // The instance may have ended already - check before touching engine variables.
        if (runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).count() > 0) {
            runtimeService.setVariable(processInstanceId, FlowVariables.LATEST_SNAPSHOT_ID, snapshotId);
        }
        // Always keep the biz row's pointer fresh so listing pages can fetch the live form
        // payload via a single column read.
        instanceRepository.findByProcessInstanceId(processInstanceId).ifPresent(biz -> {
            biz.setLatestSnapshotId(snapshotId);
            instanceRepository.save(biz);
        });
    }

    private void enforceFieldRules(Long chartId, String nodeKey, Map<String, Object> submitted,
                                   String processInstanceId) {
        if (chartId == null) {
            return;
        }
        List<FieldRuleSpec> rules = formBindingService.resolveRulesForNode(chartId, nodeKey);
        if (rules.isEmpty()) {
            return;
        }
        Map<String, Object> previous = formSnapshotService.currentPayload(processInstanceId);
        fieldPermissionEnforcer.enforce(submitted, previous, rules);
    }

    private Long currentChartId(String processInstanceId) {
        Object raw = runtimeService.getVariable(processInstanceId, FlowVariables.CHART_ID);
        return raw instanceof Number n ? n.longValue() : null;
    }

    private Long currentBoundFormId(String processInstanceId) {
        Object raw = runtimeService.getVariable(processInstanceId, FlowVariables.BOUND_FORM_ID);
        return raw instanceof Number n ? n.longValue() : null;
    }

    private void rejectTask(Task task, Map<String, Object> variables) {
        // Default reject semantics: terminate the whole instance. A future enhancement is to
        // honour a per-node "rejectTo" jump target (modelled in the DSL but not yet emitted
        // as BPMN); for now keeping the simpler "reject = end" behaviour matches what most
        // approval flows expect.
        runtimeService.deleteProcessInstance(task.getProcessInstanceId(), "rejected");
    }

    @SuppressWarnings("unchecked")
    private void appendPreviousActor(String processInstanceId, Long actorId) {
        Object raw = runtimeService.getVariable(processInstanceId, FlowVariables.PREVIOUS_ACTORS);
        java.util.List<Long> chain;
        if (raw instanceof java.util.List<?> list) {
            chain = new java.util.ArrayList<>((java.util.List<Long>) list);
        } else {
            chain = new java.util.ArrayList<>();
        }
        chain.add(actorId);
        runtimeService.setVariable(processInstanceId, FlowVariables.PREVIOUS_ACTORS, chain);
    }

    private Task requireActiveTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Task not found or already completed: " + taskId);
        }
        return task;
    }

    private void ensureTaskOwnedBy(Task task, Long actorId) {
        String assignee = task.getAssignee();
        if (assignee != null && !assignee.equals(actorId.toString())) {
            throw new IllegalStateException(
                    "Task " + task.getId() + " is owned by " + assignee + ", not by caller " + actorId);
        }
    }

    private void finaliseBizRow(String processInstanceId, FlowInstanceState terminalState) {
        instanceRepository.findByProcessInstanceId(processInstanceId).ifPresent(biz -> {
            biz.setBizStatus(terminalState.getCode());
            HistoricProcessInstance hi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            LocalDateTime endTime = hi == null || hi.getEndTime() == null
                    ? LocalDateTime.now()
                    : LocalDateTime.ofInstant(hi.getEndTime().toInstant(), ZoneId.systemDefault());
            biz.setEndTime(endTime);
            if (biz.getStartTime() != null) {
                biz.setDurationMs(java.time.Duration.between(biz.getStartTime(), endTime).toMillis());
            }
            instanceRepository.save(biz);
        });
    }

    private void appendRecord(String processInstanceId, String taskId, String nodeKey, String nodeName,
                              ApprovalAction action, Long actorId, Long targetUserId, String targetUserName,
                              String comment) {
        FlowApprovalRecordEntity record = new FlowApprovalRecordEntity();
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setNodeKey(nodeKey);
        record.setNodeName(nodeName);
        record.setActionType(action.getWire());
        record.setActorId(actorId);
        record.setActorName(permissionEvaluator.getCurrentUserName());
        record.setTargetUserId(targetUserId);
        record.setTargetUserName(targetUserName);
        record.setComment(comment);
        approvalRecordRepository.append(record);
    }

    private Long requireCurrentUserId() {
        Long userId = permissionEvaluator.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("No authenticated user in current context");
        }
        return userId;
    }

    /**
     * Builds an aggregated detail view for the front-end process-detail drawer.
     */
    @Transactional(readOnly = true)
    public FlowInstanceDetailVO getInstanceDetail(String processInstanceId) {
        // 1. 查询业务实例
        FlowInstanceBizEntity biz = instanceRepository.findByProcessInstanceId(processInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Process instance not found: " + processInstanceId));

        FlowInstanceDetailVO vo = new FlowInstanceDetailVO();
        vo.setProcessInstanceId(biz.getProcessInstanceId());
        vo.setChartKey(biz.getChartKey());
        // 通过 chartId 反查流程定义获取 chartName + chartDsl + 表单绑定信息
        definitionRepository.findById(biz.getChartId())
                .ifPresent(def -> {
                    vo.setChartName(def.getChartName());
                    vo.setChartDsl(def.getChartDsl());
                    // 从 chartDsl 解析 forms 数组，提取第一个表单绑定的元信息
                    extractFormBindingFromDsl(def.getChartDsl(), vo);
                });
        vo.setBizNo(biz.getBizNo());
        vo.setTitle(biz.getTitle());
        vo.setStarterId(biz.getStarterId());
        vo.setStarterName(biz.getStarterName());
        vo.setBizStatus(biz.getBizStatus());
        vo.setStartTime(biz.getStartTime());
        vo.setEndTime(biz.getEndTime());

        // 2. 获取当前活跃节点 + 已完成节点（用于流程图高亮）
        try {
            // 活跃节点：从 runtimeService 查询当前执行流所在的 activityId
            List<String> activeKeys = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .list()
                    .stream()
                    .map(org.flowable.engine.runtime.Execution::getActivityId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            vo.setActiveNodeKeys(activeKeys);
        } catch (Exception e) {
            // 流程已结束时 runtimeService 查不到，忽略
            log.debug("No active executions for {}: {}", processInstanceId, e.getMessage());
        }
        try {
            // 已完成节点：从 historyService 查询已完成的活动实例
            List<String> completedKeys = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .finished()
                    .list()
                    .stream()
                    .map(org.flowable.engine.history.HistoricActivityInstance::getActivityId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            vo.setCompletedNodeKeys(completedKeys);
        } catch (Exception e) {
            log.warn("Failed to load completed nodes for {}: {}", processInstanceId, e.getMessage());
        }

        // 3. 查询表单数据：优先从快照表获取，若无快照则从 Flowable 流程变量兜底
        try {
            Map<String, Object> formData = formSnapshotService.getLatestSnapshot(processInstanceId);
            if (formData == null || formData.isEmpty()) {
                formData = loadFormDataFromProcessVariables(processInstanceId);
            }
            vo.setFormData(formData);
        } catch (Exception e) {
            log.warn("Failed to load form data for {}: {}", processInstanceId, e.getMessage());
            // 最后兜底：从流程变量获取
            try {
                vo.setFormData(loadFormDataFromProcessVariables(processInstanceId));
            } catch (Exception ex) {
                log.warn("Failed to load form data from variables for {}: {}", processInstanceId, ex.getMessage());
            }
        }

        // 3. 构建 fieldKey → label 映射（优先从 binding 表获取，否则从 chartDsl 中解析 formId）
        try {
            Map<String, String> labelMap = new LinkedHashMap<>();
            // 方式1：从 flow_form_binding 表获取
            formBindingService.loadEffectiveBinding(biz.getChartId(), null)
                    .ifPresent(boundForm -> extractFieldLabels(boundForm.getSchemaJson(), labelMap));
            // 方式2：binding 表无数据，从 chartDsl 的 forms 配置中提取 formId
            if (labelMap.isEmpty()) {
                definitionRepository.findById(biz.getChartId()).ifPresent(def -> {
                    Long formId = extractFormIdFromChartDsl(def.getChartDsl());
                    if (formId != null) {
                        formQueryPort.findById(formId)
                                .ifPresent(form -> extractFieldLabels(form.getSchemaJson(), labelMap));
                    }
                });
            }
            if (!labelMap.isEmpty()) {
                vo.setFieldLabelMap(labelMap);
            }
        } catch (Exception e) {
            log.warn("Failed to build field label map for {}: {}", processInstanceId, e.getMessage());
        }

        // 4. 查询审批记录
        List<FlowApprovalRecordEntity> records = approvalRecordRepository.findByProcessInstanceId(processInstanceId);
        List<ApprovalRecordVO> recordVOs = records.stream().map(r -> {
            ApprovalRecordVO rv = new ApprovalRecordVO();
            rv.setRecordId(r.getRecordId());
            rv.setTaskId(r.getTaskId());
            rv.setNodeKey(r.getNodeKey());
            rv.setNodeName(r.getNodeName());
            rv.setActionType(r.getActionType());
            rv.setActorId(r.getActorId());
            rv.setActorName(r.getActorName());
            rv.setTargetUserId(r.getTargetUserId());
            rv.setTargetUserName(r.getTargetUserName());
            rv.setComment(r.getComment());
            rv.setOccurredAt(r.getOccurredAt());
            return rv;
        }).toList();
        vo.setRecords(recordVOs);

        return vo;
    }

    /**
     * Loads form data from Flowable process variables as a fallback when no snapshot exists.
     * Handles both running instances (via runtimeService) and completed instances (via historyService).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadFormDataFromProcessVariables(String processInstanceId) {
        // 1. 尝试从运行中的流程获取
        try {
            Object variable = runtimeService.getVariable(processInstanceId, FlowVariables.FORM_DATA);
            if (variable instanceof Map) {
                return (Map<String, Object>) variable;
            }
        } catch (Exception ignored) {
            // 流程可能已结束，runtimeService 会抛异常
        }
        // 2. 从历史流程变量中获取（流程已结束的场景）
        try {
            var historyVariable = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .variableName(FlowVariables.FORM_DATA)
                    .singleResult();
            if (historyVariable != null && historyVariable.getValue() instanceof Map) {
                return (Map<String, Object>) historyVariable.getValue();
            }
        } catch (Exception e) {
            log.warn("Failed to load form data from history variables for {}: {}", processInstanceId, e.getMessage());
        }
        return java.util.Collections.emptyMap();
    }

    /**
     * Parses the form schema JSON and extracts a flat fieldKey → label map.
     * Recursively handles nested structures: children (GROUP), tabs[].children, columns[].children.
     */
    @SuppressWarnings("unchecked")
    private void extractFieldLabels(String schemaJson, Map<String, String> labelMap) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> schema = mapper.readValue(schemaJson,
                    new TypeReference<Map<String, Object>>() {});
            Object fieldsObj = schema.get("fields");
            if (fieldsObj instanceof List<?> fields) {
                for (Object field : fields) {
                    if (field instanceof Map) {
                        extractFieldLabelsRecursive((Map<String, Object>) field, labelMap);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse form schema for field label extraction: {}", e.getMessage());
        }
    }

    /**
     * Extracts the first formId from the chart DSL's "forms" array.
     * DSL structure: {"chartKey":"...","forms":[{"type":"DYNAMIC","formId":123},...],..."nodes":[...]}
     */
    @SuppressWarnings("unchecked")
    private Long extractFormIdFromChartDsl(String chartDsl) {
        if (chartDsl == null || chartDsl.isBlank()) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> dsl = mapper.readValue(chartDsl, new TypeReference<Map<String, Object>>() {});
            Object formsObj = dsl.get("forms");
            if (formsObj instanceof List<?> forms) {
                for (Object form : forms) {
                    if (form instanceof Map<?, ?> formMap) {
                        Object formIdObj = formMap.get("formId");
                        if (formIdObj instanceof Number num) {
                            return num.longValue();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract formId from chartDsl: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Publishes a Spring event when a CUSTOM form's flow instance finishes, carrying
     * the submitUrl and business ID so that any business module listener can sync its
     * entity table status.
     */
    @SuppressWarnings("unchecked")
    private void notifyCustomFormStatusChange(String processInstanceId, FlowInstanceState terminal) {
        try {
            FlowInstanceBizEntity biz = instanceRepository.findByProcessInstanceId(processInstanceId).orElse(null);
            if (biz == null) return;

            FlowDefinitionEntity def = definitionRepository.findById(biz.getChartId()).orElse(null);
            if (def == null || def.getChartDsl() == null) return;

            // Parse chartDsl to find CUSTOM form binding
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> dsl = mapper.readValue(def.getChartDsl(), new TypeReference<Map<String, Object>>() {});
            Object formsObj = dsl.get("forms");
            if (!(formsObj instanceof List<?> forms) || forms.isEmpty()) return;
            Map<?, ?> formMap = (Map<?, ?>) forms.get(0);
            String formType = (String) formMap.get("type");
            String submitUrl = (String) formMap.get("submitUrl");
            if (!"CUSTOM".equals(formType) || submitUrl == null || submitUrl.isBlank()) return;

            // Extract form data from process variables
            Map<String, Object> formData = loadFormDataFromProcessVariables(processInstanceId);

            // Publish a domain event for business modules to react
            eventPublisher.publishEvent(new com.smart.flow.api.instance.CustomFormStatusChangeEvent(
                    processInstanceId, submitUrl, terminal.getCode(), formData));
            log.info("Published CustomFormStatusChangeEvent for processInstanceId={}, submitUrl={}, terminal={}",
                    processInstanceId, submitUrl, terminal);
        } catch (Exception e) {
            log.warn("Failed to notify custom form status change for {}: {}", processInstanceId, e.getMessage());
        }
    }

    /**
     * Parses the chartDsl JSON to extract the first form binding and populate formType/formName/formViewUrl/formSubmitUrl
     * on the detail VO. This enables the front-end to decide whether to render the built-in dynamic form
     * or redirect to a custom business page.
     */
    @SuppressWarnings("unchecked")
    private void extractFormBindingFromDsl(String chartDsl, FlowInstanceDetailVO vo) {
        if (chartDsl == null || chartDsl.isBlank()) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> dsl = mapper.readValue(chartDsl, new TypeReference<Map<String, Object>>() {});
            Object formsObj = dsl.get("forms");
            if (!(formsObj instanceof List<?> forms) || forms.isEmpty()) {
                return;
            }
            // Take the first form binding (a chart typically binds exactly one form)
            Object firstForm = forms.get(0);
            if (!(firstForm instanceof Map<?, ?> formMap)) {
                return;
            }
            String type = (String) formMap.get("type");
            String name = (String) formMap.get("name");
            String viewUrl = (String) formMap.get("viewUrl");
            String submitUrl = (String) formMap.get("submitUrl");
            vo.setFormType(type);
            vo.setFormName(name);
            vo.setFormViewUrl(viewUrl);
            vo.setFormSubmitUrl(submitUrl);
        } catch (Exception e) {
            log.warn("Failed to extract form binding from chartDsl: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void extractFieldLabelsRecursive(Map<String, Object> field, Map<String, String> labelMap) {
        String fieldKey = (String) field.get("fieldKey");
        String label = (String) field.get("label");
        if (fieldKey != null && label != null && !label.isBlank()) {
            labelMap.put(fieldKey, label);
        }
        // Recurse into children (GROUP)
        if (field.get("children") instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof Map) {
                    extractFieldLabelsRecursive((Map<String, Object>) child, labelMap);
                }
            }
        }
        // Recurse into tabs[].children
        if (field.get("tabs") instanceof List<?> tabs) {
            for (Object tab : tabs) {
                if (tab instanceof Map<?, ?> tabMap && tabMap.get("children") instanceof List<?> tabChildren) {
                    for (Object child : tabChildren) {
                        if (child instanceof Map) {
                            extractFieldLabelsRecursive((Map<String, Object>) child, labelMap);
                        }
                    }
                }
            }
        }
        // Recurse into columns[].children
        if (field.get("columns") instanceof List<?> columns) {
            for (Object col : columns) {
                if (col instanceof Map<?, ?> colMap && colMap.get("children") instanceof List<?> colChildren) {
                    for (Object child : colChildren) {
                        if (child instanceof Map) {
                            extractFieldLabelsRecursive((Map<String, Object>) child, labelMap);
                        }
                    }
                }
            }
        }
    }
}
