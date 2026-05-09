package com.smart.flow.api.instance;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * Published when a flow instance bound to a CUSTOM form reaches a terminal state
 * (approved / rejected). Business modules listen to this event and sync their
 * entity table status accordingly.
 */
@Getter
public class CustomFormStatusChangeEvent extends ApplicationEvent {

    /** Flowable process instance ID */
    private final String processInstanceId;

    /** The submitUrl configured in the CUSTOM form binding (e.g. "/travel-apply") */
    private final String submitUrl;

    /** Terminal biz status code: "1" = APPROVED, "2" = REJECTED */
    private final String terminalStatus;

    /** Form data snapshot from process variables (contains business IDs like applyId) */
    private final Map<String, Object> formData;

    public CustomFormStatusChangeEvent(String processInstanceId, String submitUrl,
                                       String terminalStatus, Map<String, Object> formData) {
        super(processInstanceId);
        this.processInstanceId = processInstanceId;
        this.submitUrl = submitUrl;
        this.terminalStatus = terminalStatus;
        this.formData = formData;
    }
}
