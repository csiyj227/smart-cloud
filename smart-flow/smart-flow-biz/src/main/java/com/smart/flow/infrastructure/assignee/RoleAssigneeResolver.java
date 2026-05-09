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
 * Picks assignees by role code(s).
 *
 * <p>Reads {@code parameters.roleCodes} (a list of strings) from the node configuration and
 * delegates to {@link OrgQueryPort#findUserIdsByRoleCodes(List, Long)}.
 *
 * <p>An empty or missing {@code roleCodes} entry is treated as a configuration error rather
 * than "match nothing" - silently swallowing the misconfiguration would route the node to its
 * fallback (often the starter), which makes the bug very hard to diagnose in production.
 */
@Component
@RequiredArgsConstructor
public class RoleAssigneeResolver implements AssigneeResolver {

    private static final String PARAM_ROLE_CODES = "roleCodes";

    private final OrgQueryPort orgQueryPort;

    @Override
    public String strategyKey() {
        return AssigneeStrategyType.ROLE.key();
    }

    @SuppressWarnings("unchecked")
    @Override
    public AssigneeResolution resolve(AssigneeContext context) {
        Object raw = context.parametersSafe().get(PARAM_ROLE_CODES);
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new IllegalArgumentException(
                    "Role assignee strategy on node '" + context.getNodeKey()
                            + "' requires a non-empty 'roleCodes' parameter");
        }
        List<String> roleCodes = (List<String>) rawList;
        List<Long> userIds = orgQueryPort.findUserIdsByRoleCodes(roleCodes, context.getTenantId());
        return AssigneeResolution.of(userIds, strategyKey());
    }
}
