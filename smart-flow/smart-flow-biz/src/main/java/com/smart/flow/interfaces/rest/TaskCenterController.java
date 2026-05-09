package com.smart.flow.interfaces.rest;

import com.smart.common.core.web.ApiResult;
import com.smart.flow.api.taskcenter.TaskCenterPage;
import com.smart.flow.api.taskcenter.TaskCenterQuery;
import com.smart.flow.application.taskcenter.TaskCenterQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Read-side REST surface used by the task-center pages. Every endpoint is gated only by the
 * blanket {@code flow_task_view} permission - row-level filtering happens inside the
 * application service based on the authenticated user, so two callers with the same
 * permission still see different data.
 *
 * <p>The {@code POST /cc/{ccId}/read} endpoint uses POST rather than PATCH because the smart
 * front-end already standardises on POST for "side-effecting actions on a resource" - keeping
 * the verb consistent across the codebase reduces the cognitive load for new contributors.
 */
@RestController
@RequestMapping("/flow/task-center")
@RequiredArgsConstructor
public class TaskCenterController {

    private final TaskCenterQueryService queryService;

    @PreAuthorize("@authz.hasPermission('flow_task_view')")
    @GetMapping("/my-todo")
    public ApiResult<TaskCenterPage> myTodo(@ModelAttribute TaskCenterQuery query) {
        return ApiResult.success(queryService.myTodo(query));
    }

    @PreAuthorize("@authz.hasPermission('flow_task_view')")
    @GetMapping("/my-done")
    public ApiResult<TaskCenterPage> myDone(@ModelAttribute TaskCenterQuery query) {
        return ApiResult.success(queryService.myDone(query));
    }

    @PreAuthorize("@authz.hasPermission('flow_task_view')")
    @GetMapping("/my-started")
    public ApiResult<TaskCenterPage> myStarted(@ModelAttribute TaskCenterQuery query) {
        return ApiResult.success(queryService.myStarted(query));
    }

    @PreAuthorize("@authz.hasPermission('flow_task_view')")
    @GetMapping("/my-cc")
    public ApiResult<TaskCenterPage> myCc(@ModelAttribute TaskCenterQuery query) {
        return ApiResult.success(queryService.myCc(query));
    }

    /**
     * Fast-path badge endpoint - returns the count of pending + claimed tasks for the caller.
     * Backed by {@code PendingCountCache}; degrades to a single COUNT(*) on cache miss.
     * Polled by the header bell icon every few seconds, so latency matters more than freshness.
     */
    @PreAuthorize("@authz.hasPermission('flow_task_view')")
    @GetMapping("/pending-count")
    public ApiResult<Long> pendingCount() {
        return ApiResult.success(queryService.pendingCount());
    }

    /**
     * Marks a CC entry as read. Idempotent: the second call is a no-op so retries from a
     * flaky network are safe. Ownership is verified inside the service (a caller can only
     * read CC entries addressed to themselves).
     */
    @PreAuthorize("@authz.hasPermission('flow_task_view')")
    @PostMapping("/cc/{ccId}/read")
    public ApiResult<Void> markCcRead(@PathVariable Long ccId) {
        queryService.markCcRead(ccId);
        return ApiResult.success();
    }
}
