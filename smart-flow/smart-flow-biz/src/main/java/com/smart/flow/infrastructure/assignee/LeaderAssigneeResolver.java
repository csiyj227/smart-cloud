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
 * Picks the N-th level leader of a reference user.
 *
 * <p>Parameters consumed:
 * <ul>
 *   <li>{@code level} - {@link Number}, defaults to 1 (direct manager);</li>
 *   <li>{@code referenceUser} - one of {@code starter} | {@code previousActor}; defaults to
 *       {@code starter}. {@code previousActor} uses the first id from
 *       {@link AssigneeContext#previousActorsSafe()}, falling back to the starter when the
 *       chain is empty (e.g. on the very first approval node).</li>
 * </ul>
 *
 * <p>The resolver tolerates a missing leader by returning an empty resolution; the caller
 * decides whether to fall back to the starter or fail the node.
 */
@Component
@RequiredArgsConstructor
public class LeaderAssigneeResolver implements AssigneeResolver {

    private static final String PARAM_LEVEL = "level";
    private static final String PARAM_REFERENCE = "referenceUser";
    private static final String REF_STARTER = "starter";
    private static final String REF_PREVIOUS = "previousActor";

    private final OrgQueryPort orgQueryPort;

    @Override
    public String strategyKey() {
        return AssigneeStrategyType.LEADER.key();
    }

    @Override
    public AssigneeResolution resolve(AssigneeContext context) {
        int level = readLevel(context);
        Long referenceUserId = pickReferenceUser(context);
        if (referenceUserId == null) {
            return AssigneeResolution.empty(strategyKey());
        }
        Long leaderId = orgQueryPort.findLeaderUserId(referenceUserId, level, context.getTenantId());
        return leaderId == null
                ? AssigneeResolution.empty(strategyKey())
                : AssigneeResolution.of(List.of(leaderId), strategyKey());
    }

    private int readLevel(AssigneeContext context) {
        Object raw = context.parametersSafe().get(PARAM_LEVEL);
        if (raw instanceof Number n) {
            int value = n.intValue();
            if (value < 1) {
                throw new IllegalArgumentException(
                        "Leader strategy on node '" + context.getNodeKey() + "' requires level >= 1, got " + value);
            }
            return value;
        }
        return 1;
    }

    private Long pickReferenceUser(AssigneeContext context) {
        Object raw = context.parametersSafe().get(PARAM_REFERENCE);
        String reference = raw instanceof String s ? s : REF_STARTER;
        if (REF_PREVIOUS.equals(reference)) {
            List<Long> previous = context.previousActorsSafe();
            return previous.isEmpty() ? context.getStarterUserId() : previous.get(0);
        }
        return context.getStarterUserId();
    }
}
