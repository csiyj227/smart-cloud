package com.smart.flow.domain.definition;

import lombok.Getter;

import java.util.Arrays;

/**
 * Lifecycle states of a {@code flow_definition} row.
 *
 * <p>The persisted column is a 1-character string (matches the
 * {@code VARCHAR(2)} schema column and the {@code "0"/"1"/"2"} convention used elsewhere in
 * the platform), but every domain-side reference goes through this enum so the calling code
 * never has to know the wire encoding.
 */
@Getter
public enum FlowDefinitionStatus {

    DRAFT("0"),
    PUBLISHED("1"),
    ARCHIVED("2");

    private final String code;

    FlowDefinitionStatus(String code) {
        this.code = code;
    }

    public static FlowDefinitionStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown FlowDefinitionStatus code: " + code));
    }
}
