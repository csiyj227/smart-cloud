package com.smart.flow.api.definition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Command for saving a draft of a flow definition.
 *
 * <p>A "draft" lives only in {@code flow_definition} - it is <strong>not</strong> deployed to
 * Flowable. This intentional separation lets designers iterate on a chart without polluting
 * the engine's history tables, and avoids the trap  of every save creating a new
 * deployment.
 *
 * <p>If {@code chartId} is {@code null} a new draft row is created (a fresh
 * {@code chartVersion} is allocated). If {@code chartId} is provided, the existing draft is
 * overwritten in place - <em>only</em> drafts can be updated this way; calling this command
 * against a published row throws.
 */
@Data
public class FlowDefinitionDraftCmd {

    /** Optional - present only when overwriting an existing draft. */
    private Long chartId;

    @NotBlank
    @Size(max = 64)
    private String chartKey;

    @NotBlank
    @Size(max = 128)
    private String chartName;

    @Size(max = 64)
    private String chartCategory;

    /** Raw FlowChart DSL JSON. Validated structurally by the compiler on publish. */
    @NotNull
    private String chartDsl;

    @Size(max = 512)
    private String description;

    @Size(max = 64)
    private String icon;

    private Integer sortOrder;

    /** Optional FK to {@code sys_form}. */
    private Long boundFormId;
}
