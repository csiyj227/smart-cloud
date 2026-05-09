package com.smart.flow.domain.form;

import com.smart.flow.infrastructure.persistence.entity.FlowFormBindingEntity;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the {@code flow_form_binding} table. The application layer talks to
 * this interface so that form-binding storage can later be swapped (e.g. moved into
 * a separate metadata service) without rewriting the application orchestration.
 */
public interface FormBindingRepository {

    /** Persist or replace the chart-level / node-level binding. */
    void upsert(FlowFormBindingEntity entity);

    /** Removes a binding by id; safe no-op if it does not exist. */
    void deleteById(Long bindingId);

    Optional<FlowFormBindingEntity> findById(Long bindingId);

    /** Lookup the chart-level default binding ({@code nodeKey IS NULL}). */
    Optional<FlowFormBindingEntity> findChartLevel(Long chartId);

    /** Lookup the override for a single node, if any. */
    Optional<FlowFormBindingEntity> findNodeLevel(Long chartId, String nodeKey);

    /** All bindings for a chart - used by the designer to render the binding overview. */
    List<FlowFormBindingEntity> listByChart(Long chartId);
}
