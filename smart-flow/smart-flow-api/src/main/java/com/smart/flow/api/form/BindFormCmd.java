package com.smart.flow.api.form;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Command sent from the designer when the chart author drops a form on a node (or the
 * chart itself).
 *
 * <p>{@code nodeKey == null} signals a chart-level binding (the default form for every
 * node that does not have its own override). A non-null {@code nodeKey} creates an
 * override that supersedes the chart-level rules for that single node.
 */
@Data
public class BindFormCmd {

    @NotNull
    private Long chartId;

    /** {@code null} = chart-level default; non-null = per-node override. */
    private String nodeKey;

    @NotNull
    private Long formId;

    /**
     * Optional. When omitted, the binding inherits the form's schema-defined defaults
     * (everything writable). When provided, every entry replaces the default for that
     * field; fields not mentioned remain at the inherited default.
     */
    private List<FieldRuleSpec> fieldRules;
}
