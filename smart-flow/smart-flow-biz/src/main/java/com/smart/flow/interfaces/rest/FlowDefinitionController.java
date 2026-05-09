package com.smart.flow.interfaces.rest;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smart.common.core.web.ApiResult;
import com.smart.flow.api.definition.FlowDefinitionDraftCmd;
import com.smart.flow.api.definition.FlowDefinitionView;
import com.smart.flow.application.definition.FlowDefinitionAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface for flow-definition lifecycle (draft / publish / archive / version listing).
 *
 * <p>Routes are mounted under {@code /flow/definition} so the gateway's existing {@code /flow/**}
 * pass-through covers them without extra config. Permission codes follow the smart-system naming
 * convention {@code <module>_<resource>_<verb>}; the {@code @authz.hasPermission(...)} bean is
 * inherited from {@code smart-common-security}.
 *
 * <p>The controller is a thin web adapter - all business logic lives in
 * {@link FlowDefinitionAppService}. We do not catch exceptions here on purpose; the global
 * advice in {@code smart-common-security} (extended by {@code FlowExceptionHandler}) takes
 * care of mapping them to the unified {@link R} envelope.
 */
@RestController
@RequestMapping("/flow/definition")
@RequiredArgsConstructor
public class FlowDefinitionController {

    private final FlowDefinitionAppService appService;

    /**
     * Saves a draft. {@code chartId == null} creates a new version, otherwise the existing
     * draft is overwritten in place. Published rows cannot be edited - the application layer
     * enforces this and surfaces a clear error message.
     */
    @PreAuthorize("@authz.hasPermission('flow_def_edit')")
    @PostMapping("/draft")
    public ApiResult<Long> saveDraft(@RequestBody @Valid FlowDefinitionDraftCmd cmd) {
        return ApiResult.success(appService.saveDraft(cmd));
    }

    /**
     * Compiles and deploys the chart to Flowable. Idempotent: calling on an already-published
     * chart simply returns the current view rather than throwing.
     */
    @PreAuthorize("@authz.hasPermission('flow_def_publish')")
    @PostMapping("/{chartId}/publish")
    public ApiResult<FlowDefinitionView> publish(@PathVariable Long chartId) {
        return ApiResult.success(appService.publish(chartId));
    }

    /**
     * Archives the version, undeploying it from Flowable so it cannot start new instances.
     * Already-running instances retain their pinned definition (Flowable history is unaffected).
     */
    @PreAuthorize("@authz.hasPermission('flow_def_archive')")
    @DeleteMapping("/{chartId}")
    public ApiResult<Void> archive(@PathVariable Long chartId) {
        appService.archive(chartId);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('flow_def_view')")
    @GetMapping("/{chartId}")
    public ApiResult<FlowDefinitionView> getDetail(@PathVariable Long chartId) {
        return ApiResult.success(appService.getDetail(chartId));
    }

    /**
     * Pagination for the designer entry list. Returns one row per chartKey at its latest
     * version (older versions are accessible through the version timeline endpoint).
     *
     * <p>Defaults are designed for the typical first-page render: 10 rows, no filters.
     */
    @PreAuthorize("@authz.hasPermission('flow_def_view')")
    @GetMapping("/page")
    public ApiResult<IPage<FlowDefinitionView>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResult.success(appService.page(current, size, keyword, status));
    }

    /**
     * Lists every version (draft + published + archived) for a given chart key, newest first.
     * Used by the designer's "version timeline" panel.
     */
    @PreAuthorize("@authz.hasPermission('flow_def_view')")
    @GetMapping("/key/{chartKey}/versions")
    public ApiResult<List<FlowDefinitionView>> listVersions(@PathVariable String chartKey) {
        return ApiResult.success(appService.listVersions(chartKey));
    }

    /**
     * Resolves the canonical "currently runnable" version - typically called by the front-end
     * when starting a new instance and only the chart key is known.
     */
    @PreAuthorize("@authz.hasPermission('flow_def_view')")
    @GetMapping("/key/{chartKey}/latest")
    public ApiResult<FlowDefinitionView> getLatestPublished(@PathVariable String chartKey) {
        return ApiResult.success(appService.getLatestPublished(chartKey));
    }

    /**
     * Convenience overwrite endpoint that mirrors the draft contract but uses HTTP PUT for
     * REST-friendly clients. Internally identical to {@link #saveDraft(FlowDefinitionDraftCmd)};
     * the chartId in the path is the authoritative source of truth and overrides the body.
     */
    @PreAuthorize("@authz.hasPermission('flow_def_edit')")
    @PutMapping("/{chartId}/draft")
    public ApiResult<Long> overwriteDraft(@PathVariable Long chartId,
                                  @RequestBody @Valid FlowDefinitionDraftCmd cmd) {
        cmd.setChartId(chartId);
        return ApiResult.success(appService.saveDraft(cmd));
    }
}
