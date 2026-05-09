package com.smart.flow.api.form;

import lombok.Getter;

import java.util.Arrays;

/**
 * Per-field permission rule applied at runtime when a user opens or submits a form bound
 * to a workflow node.
 *
 * <p>The wire value (lowercase short code) is what gets persisted in the
 * {@code flow_form_binding.field_rules} JSON column and what flows over the API. The
 * enum is the single source of truth - both the runtime enforcer and the front-end
 * consume the same vocabulary.
 *
 * <h3>Why three values, not four?</h3>
 * Some commercial designers offer a fourth "required" rule. We model "required" as a
 * field-level attribute on the form schema itself (it is owned by {@code sys_form},
 * not by the workflow binding) so the binding stays purely about <em>visibility</em>
 * and <em>writability</em>. This keeps the rule space orthogonal: the form decides
 * whether a field exists and is required at all, the binding decides whether <em>this
 * approver</em> can see / edit it.
 */
@Getter
public enum FormFieldRule {

    /** Field is shown but the input is locked. The submitted payload must keep the same value. */
    READ_ONLY("r"),

    /** Field is shown and the approver may change its value. */
    READ_WRITE("rw"),

    /** Field is hidden from the approver entirely; submitted payload must not mention it. */
    HIDDEN("hidden");

    private final String wire;

    FormFieldRule(String wire) {
        this.wire = wire;
    }

    public static FormFieldRule fromWire(String wire) {
        return Arrays.stream(values())
                .filter(r -> r.wire.equalsIgnoreCase(wire))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown form field rule: " + wire));
    }
}
