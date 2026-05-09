package com.smart.flow.domain.assignee;

/**
 * SPI for computing the candidate assignees of a single approval node.
 *
 * <p>Implementations are discovered by Spring (any {@code @Component} that implements this
 * interface is collected by {@link AssigneeResolverRegistry}). To register a custom strategy
 * the implementor only needs to:
 * <ol>
 *   <li>annotate the class with {@code @Component} so Spring picks it up;</li>
 *   <li>return a unique, stable {@link #strategyKey()} - this is the value persisted in the
 *       FlowChart DSL, so changing it later breaks existing flow definitions.</li>
 * </ol>
 *
 * <p>Implementations must be stateless and thread-safe; the same instance is reused for every
 * resolution request.
 *
 * <p><strong>Why not a functional interface?</strong> {@link #strategyKey()} is part of the
 * contract - resolvers are looked up by key, not by class - so the SPI deliberately exposes
 * two methods to discourage anonymous lambda registration.
 */
public interface AssigneeResolver {

    /**
     * The wire identifier under which this resolver is referenced from the FlowChart DSL.
     * Must be unique across the entire application context.
     */
    String strategyKey();

    /**
     * Computes the candidate assignees for the given context.
     *
     * <p>Implementations should:
     * <ul>
     *   <li>return {@link AssigneeResolution#empty(String)} (not throw) when no users match
     *       the configuration - the caller decides the fallback policy;</li>
     *   <li>throw {@link IllegalArgumentException} only for genuinely malformed parameters
     *       (e.g. a SpEL expression that fails to compile) so the calling service can
     *       distinguish "config bug" from "no candidates".</li>
     * </ul>
     */
    AssigneeResolution resolve(AssigneeContext context);
}
