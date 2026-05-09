package com.smart.flow.api.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * The set of user-visible actions that can be performed against a running task.
 *
 * <p>These are exposed both via the REST layer (the front-end task center sends one of these
 * codes when an approver clicks a button) and the audit log (each row in
 * {@code flow_approval_record} stores one of these as its {@code action_type}).
 */
public enum ApprovalAction {

    /** Recorded once per instance when the starter submits the form. */
    SUBMIT("submit"),
    APPROVE("approve"),
    REJECT("reject"),
    TRANSFER("transfer"),
    DELEGATE("delegate"),
    WITHDRAW("withdraw"),
    ADD_SIGN("addSign"),
    REMOVE_SIGN("removeSign"),
    TERMINATE("terminate"),
    COMMENT("comment"),
    CC_READ("ccRead");

    private final String wire;

    ApprovalAction(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String getWire() {
        return wire;
    }

    @JsonCreator
    public static ApprovalAction fromWire(String wire) {
        return Arrays.stream(values())
                .filter(a -> a.wire.equalsIgnoreCase(wire))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown approval action: " + wire));
    }
}
