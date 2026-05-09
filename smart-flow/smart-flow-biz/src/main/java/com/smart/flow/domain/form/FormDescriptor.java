package com.smart.flow.domain.form;

import lombok.Builder;
import lombok.Value;

/**
 * Read-only view of a {@code sys_form} row that the workflow module needs.
 *
 * <p>Intentionally narrow - we do <em>not</em> expose audit columns, tenant id or
 * status because the binding application service does not act on them. If we later
 * need to enforce "binding only allowed against published forms", the port can grow
 * a second method like {@code findPublishedById} rather than widening this DTO.
 */
@Value
@Builder
public class FormDescriptor {
    Long formId;
    String formKey;
    String formName;
    String schemaJson;
    String layoutJson;
}
