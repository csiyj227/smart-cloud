package com.smart.flow.domain.form;

import com.smart.flow.api.form.FieldRuleSpec;
import com.smart.flow.api.form.FormFieldRule;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure-domain checker that decides whether the form payload an approver submitted is
 * compatible with the per-field rules currently in effect for the node they are
 * approving.
 *
 * <p>Rule semantics (see {@link FormFieldRule}):
 * <ul>
 *   <li>{@code READ_ONLY} - the submitted value must equal the previously-stored
 *       value. Approvers cannot tamper with locked fields.</li>
 *   <li>{@code READ_WRITE} - any value (including null/missing) is accepted.</li>
 *   <li>{@code HIDDEN} - the field must be absent from the submitted payload. A hidden
 *       field that nevertheless arrives over the wire is treated as a malicious or
 *       buggy client and rejected outright.</li>
 * </ul>
 *
 * <p>Fields that have no rule are implicitly {@code READ_WRITE} (the most permissive
 * default) - this matches user intuition that "a binding override only needs to mention
 * the fields that diverge from the default".
 *
 * <p>The class is annotated as a Spring component but holds zero state; it is safe to
 * inject anywhere that needs the check.
 */
@Component
public class FieldPermissionEnforcer {

    /**
     * Validates the submitted payload against the binding's rules.
     *
     * @param submittedFields newly-submitted form payload (field name -> value)
     * @param previousFields  payload as it was at the previous step (field name -> value); used
     *                        to enforce READ_ONLY equality. May be {@code null} for the very
     *                        first submission.
     * @param rules           effective rules for this node; {@code null} or empty means "all
     *                        fields are read-write".
     * @throws FormFieldRuleViolationException if any rule is violated; the exception carries
     *                                         the offending field name and the rule it broke.
     */
    public void enforce(Map<String, Object> submittedFields,
                        Map<String, Object> previousFields,
                        List<FieldRuleSpec> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        Map<String, Object> previous = previousFields == null ? Collections.emptyMap() : previousFields;
        Map<String, Object> submitted = submittedFields == null ? Collections.emptyMap() : submittedFields;

        // Index rules by field name for O(1) lookup; later duplicates win, matching the
        // designer's "last write wins" mental model when a binding lists the same field twice.
        Map<String, FormFieldRule> ruleByField = new HashMap<>(rules.size());
        for (FieldRuleSpec spec : rules) {
            ruleByField.put(spec.getField(), spec.getRule());
        }

        for (Map.Entry<String, FormFieldRule> entry : ruleByField.entrySet()) {
            String field = entry.getKey();
            FormFieldRule rule = entry.getValue();
            switch (rule) {
                case READ_ONLY -> {
                    // Two perfectly legal cases that must NOT trip the rule:
                    //   * the approver's payload omits the field entirely (front-end didn't
                    //     re-submit a value they couldn't edit anyway);
                    //   * there was no prior value to lock against (this is the first action
                    //     on the instance, e.g. a starter form that has no snapshot yet) and
                    //     the submitter is therefore "writing the canonical first value".
                    // We only fail when the approver SENT a different value than the locked one.
                    if (!submitted.containsKey(field)) {
                        break;
                    }
                    if (previous.isEmpty()) {
                        break;
                    }
                    Object before = previous.get(field);
                    Object after = submitted.get(field);
                    if (!Objects.equals(before, after)) {
                        throw new FormFieldRuleViolationException(field, rule,
                                "read-only field cannot be modified");
                    }
                }
                case HIDDEN -> {
                    if (submitted.containsKey(field)) {
                        throw new FormFieldRuleViolationException(field, rule,
                                "hidden field must not appear in the submitted payload");
                    }
                }
                case READ_WRITE -> {
                    // No-op: any value (or absence) is allowed.
                }
                default -> throw new IllegalStateException("Unhandled rule: " + rule);
            }
        }
    }
}
