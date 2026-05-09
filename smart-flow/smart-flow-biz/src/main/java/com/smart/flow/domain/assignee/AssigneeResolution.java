package com.smart.flow.domain.assignee;

import lombok.Value;

import java.util.List;

/**
 * Outcome of an {@link AssigneeResolver} invocation.
 *
 * <p>Wrapping the user-id list in a dedicated VO (instead of returning {@code List<Long>})
 * leaves room for future metadata - for example a {@code resolverChain} field so the audit log
 * can show "assignees were computed by [role:approver, fallback:starter]" - without breaking
 * the SPI contract.
 *
 * <p>An empty list is a legitimate result (means "no candidates"); the calling service decides
 * whether to fall back to the configured backup strategy or fail the node.
 */
@Value
public class AssigneeResolution {

    /** Resolved user ids, may be empty but never {@code null}. */
    List<Long> userIds;

    /** The strategy key that produced this resolution, copied for traceability. */
    String strategyKey;

    public static AssigneeResolution empty(String strategyKey) {
        return new AssigneeResolution(List.of(), strategyKey);
    }

    public static AssigneeResolution of(List<Long> userIds, String strategyKey) {
        return new AssigneeResolution(userIds == null ? List.of() : List.copyOf(userIds), strategyKey);
    }

    public boolean isEmpty() {
        return userIds.isEmpty();
    }
}
