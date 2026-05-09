package com.smart.flow.application.definition;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.common.security.component.PermissionEvaluator;
import com.smart.flow.api.definition.FlowDefinitionDraftCmd;
import com.smart.flow.api.definition.FlowDefinitionView;
import com.smart.flow.api.exception.FlowChartCompileException;
import com.smart.flow.domain.chart.CompiledArtifact;
import com.smart.flow.domain.definition.FlowDefinitionPublishedEvent;
import com.smart.flow.domain.definition.FlowDefinitionRepository;
import com.smart.flow.domain.definition.FlowDefinitionStatus;
import com.smart.flow.infrastructure.compiler.FlowChartCompiler;
import com.smart.flow.infrastructure.flowable.FlowableDeploymentGateway;
import com.smart.flow.infrastructure.persistence.entity.FlowDefinitionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service that owns the full lifecycle of a flow definition:
 * <ol>
 *   <li>create / update <em>drafts</em> in {@code flow_definition} only - never touches
 *       Flowable;</li>
 *   <li>compile + deploy on <em>publish</em> - this is the only path that creates an
 *       {@code act_re_deployment} row;</li>
 *   <li>archive an old version, optionally undeploying it from Flowable so it can no longer
 *       be used to start new instances (running instances keep their pinned definition).</li>
 * </ol>
 *
 * <p><strong>Why split draft / publish so strictly?</strong> The reference implementations we
 * studied let every "save" trigger a Flowable deployment, which has two pernicious effects:
 * the {@code act_re_*} tables grow unbounded with throwaway iterations, and the engine cache
 * has to be busted on every keystroke. Smart's approach treats Flowable as the runtime
 * engine, not a versioned document store - {@code flow_definition} is the document store.
 *
 * <p>All write paths run inside a single transaction; the
 * {@link FlowDefinitionPublishedEvent} is published <strong>after</strong> the transaction
 * commits (handled by {@code @TransactionalEventListener} on the consumer side) so that
 * downstream listeners never see a half-committed row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDefinitionAppService {

    private final FlowDefinitionRepository repository;
    private final FlowChartCompiler compiler;
    private final FlowableDeploymentGateway deploymentGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final PermissionEvaluator permissionEvaluator;

    /**
     * Creates a new draft, or overwrites an existing one. The DSL is <strong>not</strong>
     * compiled here on purpose - we want designers to be able to save half-finished work that
     * the compiler would reject. Validation only kicks in at publish time.
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(FlowDefinitionDraftCmd cmd) {
        if (cmd.getChartId() == null) {
            return insertDraft(cmd);
        }
        return overwriteDraft(cmd);
    }

    /**
     * Compiles the draft, deploys the resulting BPMN to Flowable, and flips the row status to
     * PUBLISHED. The original DSL stays in the row for round-trip editing.
     *
     * <p>Throws {@link FlowChartCompileException} if the DSL has structural / semantic
     * errors. The transaction is rolled back in that case so neither the row nor the
     * Flowable deployment is left in a half-applied state.
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDefinitionView publish(Long chartId) {
        FlowDefinitionEntity entity = requireEntity(chartId);
        if (FlowDefinitionStatus.PUBLISHED.getCode().equals(entity.getPublishStatus())) {
            // Idempotent - returning the existing view rather than throwing keeps the API
            // friendly to retries on a flaky network.
            return toView(entity);
        }

        CompiledArtifact artifact = compiler.compileFromJson(entity.getChartDsl());
        FlowableDeploymentGateway.DeployResult result = deploymentGateway.deploy(
                entity.getChartKey(), entity.getChartVersion(), artifact.getBpmnXml());

        entity.setBpmnXml(artifact.getBpmnXml());
        entity.setDeploymentId(result.deploymentId());
        entity.setProcessDefinitionId(result.processDefinitionId());
        entity.setPublishStatus(FlowDefinitionStatus.PUBLISHED.getCode());
        repository.save(entity);

        Long publisherUserId = currentUserId();
        eventPublisher.publishEvent(new FlowDefinitionPublishedEvent(
                entity.getChartId(), entity.getChartKey(), entity.getChartVersion(),
                result.deploymentId(), result.processDefinitionId(), publisherUserId));
        log.info("Published chart {} v{} (chartId={})", entity.getChartKey(), entity.getChartVersion(), chartId);

        return toView(entity);
    }

    /**
     * Archives a published version. The Flowable deployment is removed so the version can no
     * longer start new instances - already-running instances keep their pinned definition
     * because Flowable copies the BPMN into the runtime tables on instance start.
     */
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long chartId) {
        FlowDefinitionEntity entity = requireEntity(chartId);
        if (FlowDefinitionStatus.ARCHIVED.getCode().equals(entity.getPublishStatus())) {
            return;
        }
        if (entity.getDeploymentId() != null) {
            deploymentGateway.undeploy(entity.getDeploymentId());
        }
        entity.setPublishStatus(FlowDefinitionStatus.ARCHIVED.getCode());
        repository.save(entity);
    }

    public FlowDefinitionView getDetail(Long chartId) {
        return toView(requireEntity(chartId), true);
    }

    public List<FlowDefinitionView> listVersions(String chartKey) {
        return repository.listByChartKey(chartKey).stream().map(this::toView).toList();
    }

    /**
     * Pagination for the front-end "flow definition list" page. Returns one row per chartKey
     * at its latest version - the designer entry-point cares about "which charts exist", not
     * "every historical revision".
     *
     * @param current 1-based page number
     * @param size    page size (clamped to [1, 100] to prevent unbounded queries)
     */
    public IPage<FlowDefinitionView> page(long current, long size, String keyword, String status) {
        long safeSize = Math.min(Math.max(size, 1L), 100L);
        long safeCurrent = Math.max(current, 1L);
        IPage<FlowDefinitionEntity> raw = repository.pageLatest(
                new Page<>(safeCurrent, safeSize), keyword, status);
        // Map records in-place via convert() so total/pages metadata is preserved.
        return raw.convert(this::toView);
    }

    /**
     * Resolves the canonical "currently runnable" version for a given key - typically called
     * when the front-end starts a new instance and only knows the chart key, not the version.
     */
    public FlowDefinitionView getLatestPublished(String chartKey) {
        return repository.findLatestPublished(chartKey)
                .map(this::toView)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No published version exists for chartKey=" + chartKey));
    }

    private Long insertDraft(FlowDefinitionDraftCmd cmd) {
        Integer version = repository.nextVersion(cmd.getChartKey());
        FlowDefinitionEntity entity = new FlowDefinitionEntity();
        entity.setChartKey(cmd.getChartKey());
        entity.setChartName(cmd.getChartName());
        entity.setChartCategory(cmd.getChartCategory());
        entity.setChartVersion(version);
        entity.setPublishStatus(FlowDefinitionStatus.DRAFT.getCode());
        entity.setChartDsl(cmd.getChartDsl());
        entity.setDescription(cmd.getDescription());
        entity.setIcon(cmd.getIcon());
        entity.setSortOrder(cmd.getSortOrder() == null ? 0 : cmd.getSortOrder());
        entity.setBoundFormId(cmd.getBoundFormId());
        FlowDefinitionEntity saved = repository.save(entity);
        return saved.getChartId();
    }

    private Long overwriteDraft(FlowDefinitionDraftCmd cmd) {
        FlowDefinitionEntity existing = requireEntity(cmd.getChartId());
        if (!FlowDefinitionStatus.DRAFT.getCode().equals(existing.getPublishStatus())) {
            // Editing a published row would silently mutate what running instances were started
            // against. Forcing the user to "create new version" surfaces that intent loudly.
            throw new IllegalStateException(
                    "chartId=" + cmd.getChartId() + " is not a draft; create a new version instead");
        }
        existing.setChartName(cmd.getChartName());
        existing.setChartCategory(cmd.getChartCategory());
        existing.setChartDsl(cmd.getChartDsl());
        existing.setDescription(cmd.getDescription());
        existing.setIcon(cmd.getIcon());
        if (cmd.getSortOrder() != null) {
            existing.setSortOrder(cmd.getSortOrder());
        }
        existing.setBoundFormId(cmd.getBoundFormId());
        repository.save(existing);
        return existing.getChartId();
    }

    private FlowDefinitionEntity requireEntity(Long chartId) {
        return repository.findById(chartId)
                .orElseThrow(() -> new IllegalArgumentException("FlowDefinition not found: chartId=" + chartId));
    }

    private FlowDefinitionView toView(FlowDefinitionEntity entity) {
        return toView(entity, false);
    }

    private FlowDefinitionView toView(FlowDefinitionEntity entity, boolean includeDsl) {
        return FlowDefinitionView.builder()
                .chartId(entity.getChartId())
                .chartKey(entity.getChartKey())
                .chartName(entity.getChartName())
                .chartCategory(entity.getChartCategory())
                .chartVersion(entity.getChartVersion())
                .publishStatus(entity.getPublishStatus())
                .chartDsl(includeDsl ? entity.getChartDsl() : null)
                .deploymentId(entity.getDeploymentId())
                .processDefinitionId(entity.getProcessDefinitionId())
                .boundFormId(entity.getBoundFormId())
                .description(entity.getDescription())
                .icon(entity.getIcon())
                .sortOrder(entity.getSortOrder())
                .createBy(entity.getCreateBy())
                .createTime(entity.getCreateTime())
                .updateBy(entity.getUpdateBy())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private Long currentUserId() {
        try {
            return permissionEvaluator.getCurrentUserId();
        } catch (Exception ignored) {
            // Falling back to null is fine for system-triggered publish (e.g. seed data import).
            return null;
        }
    }
}
