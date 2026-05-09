package com.smart.flow.api.instance;

import lombok.Getter;

import java.util.Arrays;

/**
 * Public, wire-stable enumeration of the lifecycle states a {@code flow_instance_biz} row can
 * be in.
 *
 * <p>The persisted column is a 1-character string to match the existing 0/1/2 convention used
 * across the platform; consumers should always go through this enum rather than comparing the
 * raw codes.
 */
@Getter
public enum FlowInstanceState {

    RUNNING("0"),
    APPROVED("1"),
    REJECTED("2"),
    WITHDRAWN("3"),
    TERMINATED("4");

    private final String code;

    FlowInstanceState(String code) {
        this.code = code;
    }

    public static FlowInstanceState fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown FlowInstanceState code: " + code));
    }
}
