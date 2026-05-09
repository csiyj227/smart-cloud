package com.smart.flow.api.form;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

/**
 * Single (field, rule) pair as stored in {@code flow_form_binding.field_rules}.
 *
 * <p>Two design choices worth flagging:
 * <ol>
 *   <li>The wire format is a JSON array of these objects rather than a JSON object keyed
 *       by field name. Arrays preserve declaration order, which the front-end uses to
 *       drive the per-field UI ordering when the binding overrides the form layout.</li>
 *   <li>The field name is matched case-sensitively against
 *       {@code SysForm.schema}'s field keys; the front-end designer is responsible for
 *       enforcing a consistent casing convention.</li>
 * </ol>
 */
@Getter
@ToString
public final class FieldRuleSpec {

    private final String field;
    private final FormFieldRule rule;

    @JsonCreator
    public FieldRuleSpec(@JsonProperty("field") String field,
                         @JsonProperty("rule") FormFieldRule rule) {
        this.field = Objects.requireNonNull(field, "field");
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    /**
     * Convenience constructor that decodes the wire string ("r" / "rw" / "hidden") into
     * the strongly-typed enum value. Used by the JSON deserialiser when
     * {@code field_rules} payloads come in via REST.
     */
    public static FieldRuleSpec of(String field, String wireRule) {
        return new FieldRuleSpec(field, FormFieldRule.fromWire(wireRule));
    }
}
