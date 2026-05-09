package com.smart.flow.domain.form;

import java.util.Optional;

/**
 * Outbound port to fetch the canonical form schema by id.
 *
 * <p>The current default adapter is a local PostgreSQL read of the {@code sys_form}
 * table that smart-system also writes to (smart-flow and smart-system share a database in
 * the standard deployment). The port is here so the team can later switch to a Feign
 * client when the platform splits forms into a dedicated service - the workflow code
 * itself does not change.
 *
 * <p>Returning a value object rather than a raw entity is deliberate: the workflow
 * module never needs to update form metadata, only read it, so we hand back an
 * immutable {@link FormDescriptor} and keep the upstream entity hidden.
 */
public interface FormQueryPort {

    Optional<FormDescriptor> findById(Long formId);
}
