package com.smart.flow.infrastructure.flowable;

/**
 * Centralised catalogue of the process-variable keys that smart-flow stores on every running
 * Flowable instance.
 *
 * <p>Keeping these in one place avoids the typical "magic-string-everywhere" problem and lets
 * us refactor the wire vocabulary in a single edit. All keys are intentionally namespaced
 * with a {@code smart_} prefix so they cannot collide with user-supplied form variables.
 */
public final class FlowVariables {

    /** The user id that started the process instance. */
    public static final String STARTER_ID = "smart_starterId";

    /** The chart key (logical, version-independent) - useful for activity listeners. */
    public static final String CHART_KEY = "smart_chartKey";

    /** The chart version pinned at start time. */
    public static final String CHART_VERSION = "smart_chartVersion";

    /** Tenant id captured from the starter's context. */
    public static final String TENANT_ID = "smart_tenantId";

    /** Form payload as a {@code Map<String, Object>}. */
    public static final String FORM_DATA = "smart_formData";

    /** Comma-separated list of previous-actor user ids. */
    public static final String PREVIOUS_ACTORS = "smart_previousActors";

    /** Chart database id pinned at start - lets listeners look up bindings without re-resolving by key+version. */
    public static final String CHART_ID = "smart_chartId";

    /** Form id bound at the chart level - looked up from {@code flow_definition.bound_form_id}. */
    public static final String BOUND_FORM_ID = "smart_boundFormId";

    /** Latest snapshot id, kept on the engine variables so listeners can append to a chain. */
    public static final String LATEST_SNAPSHOT_ID = "smart_latestSnapshotId";

    private FlowVariables() {
    }
}
