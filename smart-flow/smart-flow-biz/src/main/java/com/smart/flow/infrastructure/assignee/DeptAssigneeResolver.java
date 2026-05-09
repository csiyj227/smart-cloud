package com.smart.flow.infrastructure.assignee;

import com.smart.flow.api.assignee.AssigneeStrategyType;
import com.smart.flow.domain.assignee.AssigneeContext;
import com.smart.flow.domain.assignee.AssigneeResolution;
import com.smart.flow.domain.assignee.AssigneeResolver;
import com.smart.flow.domain.org.OrgQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Picks assignees by department membership.
 *
 * <p>Parameters consumed:
 * <ul>
 *   <li>{@code deptIds} - list of {@link Long}, required;</li>
 *   <li>{@code includeChildren} - boolean, defaults to {@code false}; when {@code true} the
 *       adapter walks the dept tree and includes descendants too.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DeptAssigneeResolver implements AssigneeResolver {

    private static final String PARAM_DEPT_IDS = "deptIds";
    private static final String PARAM_INCLUDE_CHILDREN = "includeChildren";

    private final OrgQueryPort orgQueryPort;

    @Override
    public String strategyKey() {
        return AssigneeStrategyType.DEPT.key();
    }

    @SuppressWarnings("unchecked")
    @Override
    public AssigneeResolution resolve(AssigneeContext context) {
        Object raw = context.parametersSafe().get(PARAM_DEPT_IDS);
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dept assignee strategy on node '" + context.getNodeKey()
                            + "' requires a non-empty 'deptIds' parameter");
        }
        List<Long> deptIds = ((List<Number>) rawList).stream()
                .map(Number::longValue)
                .toList();
        boolean includeChildren = Boolean.TRUE.equals(context.parametersSafe().get(PARAM_INCLUDE_CHILDREN));
        List<Long> userIds = orgQueryPort.findUserIdsByDeptIds(deptIds, includeChildren, context.getTenantId());
        return AssigneeResolution.of(userIds, strategyKey());
    }
}
