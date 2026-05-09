package com.smart.flow.domain.assignee;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable runtime context handed to every {@link AssigneeResolver}.
 *
 * <p>Designed as a value object so resolvers can be safely cached and reused across threads.
 * The fields capture the four pieces of information that approval-routing rules typically need:
 * <ul>
 *   <li>identifiers - {@code processInstanceId}, {@code nodeKey}, {@code tenantId};</li>
 *   <li>actor history - {@code starterUserId} and {@code previousActorUserIds} so that
 *       "leader of previous approver" / "starter's department" rules can be implemented;</li>
 *   <li>node configuration - the {@code parameters} map declared on the node, which is the only
 *       place a resolver should look for its own configuration (role codes, dept ids, ...);</li>
 *   <li>business payload - {@code formVariables} for form-field-driven rules.</li>
 * </ul>
 *
 * <p>Lists/maps are wrapped with {@link Collections#unmodifiableList} on construction so that
 * resolver implementations cannot mutate shared state by accident.
 */
@Value
@Builder
public class AssigneeContext {

    String processInstanceId;
    String nodeKey;
    Long tenantId;
    Long starterUserId;
    List<Long> previousActorUserIds;
    Map<String, Object> parameters;
    Map<String, Object> formVariables;

    /** Returns the previous-actor list as an unmodifiable view, never {@code null}. */
    public List<Long> previousActorsSafe() {
        return previousActorUserIds == null ? List.of() : Collections.unmodifiableList(previousActorUserIds);
    }

    /** Returns the parameter map as an unmodifiable view, never {@code null}. */
    public Map<String, Object> parametersSafe() {
        return parameters == null ? Map.of() : Collections.unmodifiableMap(parameters);
    }

    /** Returns the form-variable map as an unmodifiable view, never {@code null}. */
    public Map<String, Object> formVariablesSafe() {
        return formVariables == null ? Map.of() : Collections.unmodifiableMap(formVariables);
    }
}
