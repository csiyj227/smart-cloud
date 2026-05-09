package com.smart.flow.api.form;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * What the front-end actually renders when an approver opens a task: the merged form
 * schema + the effective per-field rules for that node.
 *
 * <p>"Effective" means: chart-level defaults overlaid with the node-level overrides
 * (if any) that the binding service computes. The front-end never has to know about
 * the inheritance rules.
 */
@Data
@Builder
public class BoundFormView {

    /** Source form id from {@code sys_form}. */
    private Long formId;

    private String formKey;
    private String formName;

    /** Raw {@code sys_form.schema} JSON, untouched - the front-end designer owns its shape. */
    private String schemaJson;

    /** Raw {@code sys_form.layout} JSON, untouched. */
    private String layoutJson;

    /**
     * Effective rules for this binding context. May be empty, in which case every field
     * is implicitly read-write (the most permissive default).
     */
    private List<FieldRuleSpec> effectiveRules;
}
