package com.smart.flow.api.instance;

import com.smart.flow.api.dsl.ApprovalAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Command for an approver finishing a single task.
 *
 * <p>The same shape covers both APPROVE and REJECT (and any future flavour like SIGN-OFF):
 * the {@code action} discriminator is what selects the engine code path. Keeping a single
 * command class avoids the explosion of near-identical DTOs typical of Flowable wrappers.
 */
@Data
public class CompleteTaskCmd {

    @NotBlank
    private String taskId;

    @NotNull
    private ApprovalAction action;

    private String comment;

    /** Optional updated form payload (will be persisted as a snapshot). */
    private Map<String, Object> formData;

    /** Attachment metadata (kept opaque - usually a list of file URLs/refs). */
    private List<Object> attachments;
}
