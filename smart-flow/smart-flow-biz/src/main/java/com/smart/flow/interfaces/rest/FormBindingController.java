package com.smart.flow.interfaces.rest;

import com.smart.common.core.web.ApiResult;
import com.smart.flow.api.form.BindFormCmd;
import com.smart.flow.api.form.BoundFormView;
import com.smart.flow.application.form.FormBindingAppService;
import com.smart.flow.application.form.FormSnapshotAppService;
import com.smart.flow.infrastructure.persistence.entity.FlowFormSnapshotEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST surface for both halves of the form / flow integration:
 * <ul>
 *   <li><strong>Designer side</strong>: {@code POST /binding} attaches a form to a chart or
 *       node, {@code DELETE} removes the binding;</li>
 *   <li><strong>Runtime side</strong>: {@code GET /binding/effective} computes the merged view
 *       (chart-default + node-override) the front-end shows when an approver opens a task,
 *       and {@code GET /snapshot/*} powers the audit-trail timeline.</li>
 * </ul>
 *
 * <p>The split into two URL roots ({@code /binding} and {@code /snapshot}) mirrors the two
 * separate application services on the server side, which keeps method ownership obvious
 * when reading the file. Returning the raw {@link FlowFormSnapshotEntity} for the timeline is
 * deliberate: the entity is already an immutable persistence record (no behaviour) and the
 * extra DTO would add zero information.
 */
@RestController
@RequestMapping("/flow/form")
@RequiredArgsConstructor
public class FormBindingController {

    private final FormBindingAppService bindingService;
    private final FormSnapshotAppService snapshotService;

    @PreAuthorize("@authz.hasPermission('flow_form_bind')")
    @PostMapping("/binding")
    public ApiResult<Long> bind(@RequestBody @Valid BindFormCmd cmd) {
        return ApiResult.success(bindingService.bind(cmd));
    }

    @PreAuthorize("@authz.hasPermission('flow_form_bind')")
    @DeleteMapping("/binding/{bindingId}")
    public ApiResult<Void> unbind(@PathVariable Long bindingId) {
        bindingService.unbind(bindingId);
        return ApiResult.success();
    }

    /**
     * Returns the effective binding (form schema + per-field rules) for a chart's node, or
     * the chart-level default when {@code nodeKey} is omitted. Used when an approver opens
     * a task to render the form with the right read-only / hidden fields applied.
     */
    @PreAuthorize("@authz.hasPermission('flow_task_view')")
    @GetMapping("/binding/effective")
    public ApiResult<BoundFormView> loadEffectiveBinding(@RequestParam Long chartId,
                                                 @RequestParam(required = false) String nodeKey) {
        return ApiResult.success(bindingService.loadEffectiveBinding(chartId, nodeKey).orElse(null));
    }

    /**
     * Returns the full snapshot timeline for an instance, oldest first. Powers the audit
     * panel that shows "what each approver saw and submitted" - rarely opened, so we do not
     * paginate.
     */
    @PreAuthorize("@authz.hasPermission('flow_task_view')")
    @GetMapping("/snapshot/{processInstanceId}/timeline")
    public ApiResult<List<FlowFormSnapshotEntity>> listSnapshotTimeline(@PathVariable String processInstanceId) {
        return ApiResult.success(snapshotService.listTimeline(processInstanceId));
    }

    /**
     * Returns the live form payload (the most recent snapshot, decoded). Used by the task
     * detail page to pre-fill the form before showing it to the next approver.
     */
    @PreAuthorize("@authz.hasPermission('flow_task_view')")
    @GetMapping("/snapshot/{processInstanceId}/current")
    public ApiResult<Map<String, Object>> currentSnapshot(@PathVariable String processInstanceId) {
        return ApiResult.success(snapshotService.currentPayload(processInstanceId));
    }
}
