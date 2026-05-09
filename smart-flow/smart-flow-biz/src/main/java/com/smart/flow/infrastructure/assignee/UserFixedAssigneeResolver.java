package com.smart.flow.infrastructure.assignee;

import com.smart.flow.api.assignee.AssigneeStrategyType;
import com.smart.flow.domain.assignee.AssigneeContext;
import com.smart.flow.domain.assignee.AssigneeResolution;
import com.smart.flow.domain.assignee.AssigneeResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Assigns the task to one or more explicitly specified user ids.
 *
 * <p>The node configuration {@code parameters} is expected to contain a {@code "userIds"} entry
 * that is either:
 * <ul>
 *   <li>a {@code List<Number>} (from JSON array: {@code [1, 2, 3]})</li>
 *   <li>a comma-separated string: {@code "1,2,3"}</li>
 *   <li>a single {@code Number}</li>
 * </ul>
 *
 * <p>This is the simplest strategy: no org-chart lookup, no expression evaluation - just
 * "these exact users should handle this node". Useful for admin-approval nodes, test flows,
 * or any case where the assignee is known at design time.
 */
@Component
public class UserFixedAssigneeResolver implements AssigneeResolver {

    @Override
    public String strategyKey() {
        return AssigneeStrategyType.USER_FIXED.key();
    }

    @Override
    public AssigneeResolution resolve(AssigneeContext context) {
        Object raw = context.parametersSafe().get("userIds");
        if (raw == null) {
            return AssigneeResolution.empty(strategyKey());
        }

        List<Long> userIds = parseUserIds(raw);
        return userIds.isEmpty()
                ? AssigneeResolution.empty(strategyKey())
                : AssigneeResolution.of(userIds, strategyKey());
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseUserIds(Object raw) {
        List<Long> result = new ArrayList<>();

        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                Long id = toLong(item);
                if (id != null) result.add(id);
            }
        } else if (raw instanceof String str) {
            for (String part : str.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        result.add(Long.parseLong(trimmed));
                    } catch (NumberFormatException ignored) {
                        // skip malformed entries
                    }
                }
            }
        } else {
            Long id = toLong(raw);
            if (id != null) result.add(id);
        }

        return result;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
