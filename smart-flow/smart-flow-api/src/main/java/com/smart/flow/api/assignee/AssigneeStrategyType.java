package com.smart.flow.api.assignee;

/**
 * Built-in assignee strategy identifiers.
 *
 * <p>The wire DSL stores the strategy as a free-form string so customers can plug in their own
 * implementations without touching the API jar. This enum exists purely as a typed catalogue of
 * the strategies we ship out of the box - it is never serialised into the DSL itself.
 *
 * <p>Each constant exposes its wire {@code key} explicitly rather than relying on
 * {@link Enum#name()}; this lets us rename a Java constant later without breaking persisted
 * flow definitions.
 */
public enum AssigneeStrategyType {

    /** Pick users by their role codes. */
    ROLE("role"),
    /** Pick users belonging to one or more departments (optionally including child depts). */
    DEPT("dept"),
    /** Pick the direct (or N-th) leader of the previous step's actor / starter. */
    LEADER("leader"),
    /** Reuse the process starter as the assignee. */
    STARTER("starter"),
    /** Read the assignee user id(s) from a field on the bound business form. */
    FORM_FIELD("formField"),
    /** Evaluate a SpEL expression against the runtime context to compute assignees. */
    SPEL("spel"),
    /** Assign to one or more explicitly specified user ids. */
    USER_FIXED("user-fixed");

    private final String key;

    AssigneeStrategyType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
