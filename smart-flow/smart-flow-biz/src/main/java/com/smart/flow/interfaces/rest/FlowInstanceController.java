package com.smart.flow.interfaces.rest;

import com.smart.common.core.web.ApiResult;
import com.smart.flow.api.instance.CompleteTaskCmd;
import com.smart.flow.api.instance.FlowInstanceDetailVO;
import com.smart.flow.api.instance.StartFlowCmd;
import com.smart.flow.api.instance.TransferTaskCmd;
import com.smart.flow.application.instance.FlowInstanceAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the runtime flow-instance lifecycle.
 *
 * <p>Endpoint design notes:
 * <ul>
 *   <li>{@code start} returns the {@code processInstanceId} as the body so callers do not need
 *       to extract it from a header - this keeps async client code (the front-end "start and
 *       jump to detail" flow) trivially simple.</li>
 *   <li>{@code complete} is one endpoint covering both APPROVE and REJECT to avoid the
 *       endpoint-explosion seen in some legacy systems; the {@code action} field on the body
 *       is the discriminator.</li>
 *   <li>{@code suspend} / {@code resume} / {@code terminate} are admin-only operations gated
 *       by a coarser permission code; the application layer does not double-check the caller
 *       identity because the permission grant is the contract.</li>
 * </ul>
 */
@RestController
@RequestMapping("/flow/instance")
@RequiredArgsConstructor
public class FlowInstanceController {

    private final FlowInstanceAppService appService;

    @PreAuthorize("@authz.hasPermission('flow_inst_start')")
    @PostMapping("/start")
    public ApiResult<String> start(@RequestBody @Valid StartFlowCmd cmd) {
        return ApiResult.success(appService.start(cmd));
    }

    /**
     * Approves or rejects a single task. The same DTO covers both flavours; rejecting ends
     * the instance (default semantics) while approving advances Flowable to the next node.
     */
    @PreAuthorize("@authz.hasPermission('flow_inst_complete')")
    @PostMapping("/complete")
    public ApiResult<Void> complete(@RequestBody @Valid CompleteTaskCmd cmd) {
        appService.complete(cmd);
        return ApiResult.success();
    }

    /**
     * Permanent ownership move ({@code temporary=false}) or temporary delegate
     * ({@code temporary=true}). The application layer records both flavours in
     * {@code flow_delegation} so the audit trail can distinguish them.
     */
    @PreAuthorize("@authz.hasPermission('flow_inst_transfer')")
    @PostMapping("/transfer")
    public ApiResult<Void> transfer(@RequestBody @Valid TransferTaskCmd cmd) {
        appService.transfer(cmd);
        return ApiResult.success();
    }

    /**
     * Starter-only withdraw. Currently terminates the running instance; a richer "rewind to
     * starter" mode is on the roadmap but intentionally out of scope for M2.
     */
    @PreAuthorize("@authz.hasPermission('flow_inst_withdraw')")
    @PostMapping("/{processInstanceId}/withdraw")
    public ApiResult<Void> withdraw(@PathVariable String processInstanceId,
                            @RequestParam(required = false) String comment) {
        appService.withdraw(processInstanceId, comment);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('flow_inst_admin')")
    @PostMapping("/{processInstanceId}/suspend")
    public ApiResult<Void> suspend(@PathVariable String processInstanceId) {
        appService.suspend(processInstanceId);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('flow_inst_admin')")
    @PostMapping("/{processInstanceId}/resume")
    public ApiResult<Void> resume(@PathVariable String processInstanceId) {
        appService.resume(processInstanceId);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('flow_inst_admin')")
    @PostMapping("/{processInstanceId}/terminate")
    public ApiResult<Void> terminate(@PathVariable String processInstanceId,
                             @RequestParam(required = false) String reason) {
        appService.terminate(processInstanceId, reason);
        return ApiResult.success();
    }

    /**
     * Aggregated detail view of a process instance: biz info + form snapshot + approval records.
     */
    @PreAuthorize("@authz.hasPermission('flow_task_view')")
    @GetMapping("/{processInstanceId}/detail")
    public ApiResult<FlowInstanceDetailVO> detail(@PathVariable String processInstanceId) {
        return ApiResult.success(appService.getInstanceDetail(processInstanceId));
    }
}
