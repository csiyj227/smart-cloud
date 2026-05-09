package com.smart.flow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Persistence row for {@code flow_form_binding}.
 *
 * <p>One chart_id can have multiple rows - exactly one with {@code nodeKey == null}
 * (the chart-level default) and zero or more with a non-null {@code nodeKey}
 * (per-node overrides). The application layer is responsible for enforcing this
 * invariant; the database only has a unique-by-(chart_id, node_key) index.
 *
 * <p>{@code fieldRules} is stored as JSON text and decoded by the application layer
 * into {@code List&lt;FieldRuleSpec&gt;}. We deliberately do not use a JSONB column so
 * that the table is portable to non-PostgreSQL backends.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_form_binding")
@TenantEntity
public class FlowFormBindingEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long bindingId;

    private Long chartId;

    /** Null = chart-level default; non-null = per-node override. */
    private String nodeKey;

    private Long formId;

    /** JSON array of {field, rule} objects. */
    private String fieldRules;
}
