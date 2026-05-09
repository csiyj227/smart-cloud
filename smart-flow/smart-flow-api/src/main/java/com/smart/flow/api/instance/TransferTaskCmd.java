package com.smart.flow.api.instance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Command for {@code transfer} (permanent ownership move) and {@code delegate} (temporary
 * ownership move that returns to the original owner on completion).
 *
 * <p>The boolean {@code temporary} discriminates between the two operations - a single shape
 * keeps the REST surface small while preserving the audit-log semantics.
 */
@Data
public class TransferTaskCmd {

    @NotBlank
    private String taskId;

    @NotNull
    private Long toUserId;

    /** {@code true} = delegate (returns), {@code false} = transfer (permanent). */
    private boolean temporary;

    private String reason;
}
