package com.smart.flow.infrastructure.assignee;

import com.smart.flow.api.assignee.AssigneeStrategyType;
import com.smart.flow.domain.assignee.AssigneeContext;
import com.smart.flow.domain.assignee.AssigneeResolution;
import com.smart.flow.domain.assignee.AssigneeResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the assignee user id(s) from a configurable field on the bound business form.
 *
 * <p>Parameters consumed:
 * <ul>
 *   <li>{@code fieldName} - the form variable to look up, required;</li>
 *   <li>{@code allowMultiple} - boolean, defaults to {@code false}. When {@code false} the
 *       field must resolve to a single number; when {@code true} a list/array of numbers is
 *       also accepted.</li>
 * </ul>
 *
 * <p>The resolver intentionally accepts both {@link Number} and numeric strings, because form
 * payloads coming from the front-end are JSON and JavaScript notoriously coerces large
 * numbers to strings to avoid precision loss. Anything that cannot be parsed as a positive
 * {@code long} is rejected loudly - silently dropping malformed entries would mask form-design
 * mistakes.
 */
@Component
public class FormFieldAssigneeResolver implements AssigneeResolver {

    private static final String PARAM_FIELD_NAME = "fieldName";
    private static final String PARAM_ALLOW_MULTIPLE = "allowMultiple";

    @Override
    public String strategyKey() {
        return AssigneeStrategyType.FORM_FIELD.key();
    }

    @Override
    public AssigneeResolution resolve(AssigneeContext context) {
        String fieldName = readFieldName(context);
        boolean allowMultiple = Boolean.TRUE.equals(context.parametersSafe().get(PARAM_ALLOW_MULTIPLE));
        Object value = context.formVariablesSafe().get(fieldName);
        if (value == null) {
            return AssigneeResolution.empty(strategyKey());
        }

        List<Long> userIds = new ArrayList<>();
        if (value instanceof List<?> list) {
            if (!allowMultiple && list.size() > 1) {
                throw new IllegalArgumentException(
                        "Form-field strategy on node '" + context.getNodeKey()
                                + "' got " + list.size() + " values from '" + fieldName
                                + "' but allowMultiple=false");
            }
            for (Object element : list) {
                userIds.add(parseUserId(element, fieldName, context.getNodeKey()));
            }
        } else {
            userIds.add(parseUserId(value, fieldName, context.getNodeKey()));
        }
        return AssigneeResolution.of(userIds, strategyKey());
    }

    private String readFieldName(AssigneeContext context) {
        Object raw = context.parametersSafe().get(PARAM_FIELD_NAME);
        if (!(raw instanceof String name) || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Form-field strategy on node '" + context.getNodeKey()
                            + "' requires a non-blank 'fieldName' parameter");
        }
        return name;
    }

    private Long parseUserId(Object raw, String fieldName, String nodeKey) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through to the failure message below
            }
        }
        throw new IllegalArgumentException(
                "Form-field strategy on node '" + nodeKey
                        + "' could not coerce field '" + fieldName + "' value '" + raw + "' to a user id");
    }
}
