package com.smart.flow.infrastructure.assignee;

import com.smart.flow.api.assignee.AssigneeStrategyType;
import com.smart.flow.domain.assignee.AssigneeContext;
import com.smart.flow.domain.assignee.AssigneeResolution;
import com.smart.flow.domain.assignee.AssigneeResolver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Routes the node back to the user who started the process instance.
 *
 * <p>Commonly used as the universal fallback for "self-correction" nodes (re-submission after
 * a rejection) or as the catch-all "backup" strategy for other resolvers that returned empty.
 * Takes no parameters - it intentionally has zero configuration surface so it cannot fail at
 * runtime.
 */
@Component
public class StarterAssigneeResolver implements AssigneeResolver {

    @Override
    public String strategyKey() {
        return AssigneeStrategyType.STARTER.key();
    }

    @Override
    public AssigneeResolution resolve(AssigneeContext context) {
        Long starterId = context.getStarterUserId();
        return starterId == null
                ? AssigneeResolution.empty(strategyKey())
                : AssigneeResolution.of(List.of(starterId), strategyKey());
    }
}
