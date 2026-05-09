package com.smart.flow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Persistence model of {@code flow_definition}.
 *
 * <p>Each row is one <em>version</em> of a logically-named chart. The original FlowChart DSL
 * and the compiled BPMN XML are persisted side-by-side so that the designer can re-open the
 * chart for editing without information loss while Flowable always reads the BPMN form.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flow_definition")
@TenantEntity
public class FlowDefinitionEntity extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long chartId;

    /** Logical key, identical across versions. */
    private String chartKey;

    private String chartName;

    private String chartCategory;

    /** Monotonically increasing within a (chartKey, tenantId) tuple. */
    private Integer chartVersion;

    /** "0" draft, "1" published, "2" archived. */
    private String publishStatus;

    /** Wire-format JSON of the chart, kept for round-trip editing. */
    private String chartDsl;

    /** Compiled BPMN 2.0 XML, the source of truth for the engine. */
    private String bpmnXml;

    /** Set on publish, references Flowable's act_re_deployment.id_. */
    private String deploymentId;

    /** Set on publish, references Flowable's act_re_procdef.id_. */
    private String processDefinitionId;

    /** Optional FK to sys_form, denoting the default form bound to this chart. */
    private Long boundFormId;

    private String description;

    private String icon;

    private Integer sortOrder;
}
