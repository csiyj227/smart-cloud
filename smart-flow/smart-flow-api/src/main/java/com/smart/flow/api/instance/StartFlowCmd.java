package com.smart.flow.api.instance;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Command for starting a new instance from a published flow definition.
 *
 * <p>The chart is identified by {@code chartKey} (the latest published version is resolved
 * server-side); explicit {@code chartVersion} is supported for the rare case where a caller
 * needs to pin a specific version. {@code formData} carries the initial form payload and is
 * exposed inside the engine as a process variable so that downstream resolvers (e.g.
 * {@code form-field}) and conditional gateways can reference it.
 */
@Data
public class StartFlowCmd {

    @NotBlank
    private String chartKey;

    /** Optional - defaults to the latest published version of {@code chartKey}. */
    private Integer chartVersion;

    private String title;

    /** Optional override for the biz-no prefix; defaults to the chart key uppercased. */
    private String bizNoPrefix;

    private Map<String, Object> formData;
}
