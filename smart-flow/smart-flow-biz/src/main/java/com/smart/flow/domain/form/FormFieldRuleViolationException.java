package com.smart.flow.domain.form;

import com.smart.flow.api.form.FormFieldRule;
import lombok.Getter;

/**
 * Thrown by {@link FieldPermissionEnforcer} when the submitted form payload breaks a
 * binding's per-field rule. Carries the offending field and rule so the REST layer can
 * surface a precise error to the front-end.
 */
@Getter
public class FormFieldRuleViolationException extends RuntimeException {

    private final String field;
    private final FormFieldRule rule;
    /**
     * Short, machine-friendly reason (e.g. "read-only field cannot be modified") - separated
     * from the human {@link #getMessage()} so the REST layer can return it as a structured
     * field instead of asking clients to parse the prose.
     */
    private final String reason;

    public FormFieldRuleViolationException(String field, FormFieldRule rule, String reason) {
        super("Field '" + field + "' violates rule " + rule + ": " + reason);
        this.field = field;
        this.rule = rule;
        this.reason = reason;
    }
}
