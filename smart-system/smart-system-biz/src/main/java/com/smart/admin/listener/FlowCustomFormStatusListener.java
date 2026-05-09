package com.smart.admin.listener;

import com.smart.admin.entity.BizTravelApply;
import com.smart.admin.service.BizTravelApplyService;
import com.smart.flow.api.instance.CustomFormStatusChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to {@link CustomFormStatusChangeEvent} published by the flow engine
 * when a CUSTOM-form-bound instance reaches a terminal state, and syncs the
 * corresponding business entity table status.
 *
 * <p>Currently handles:
 * <ul>
 *   <li>{@code /travel-apply} → {@link BizTravelApply} status update</li>
 * </ul>
 *
 * To add more business modules, simply add another if-branch matching the submitUrl.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowCustomFormStatusListener {

    private final BizTravelApplyService travelApplyService;

    @EventListener
    public void onCustomFormStatusChange(CustomFormStatusChangeEvent event) {
        String submitUrl = event.getSubmitUrl();
        String terminalStatus = event.getTerminalStatus();

        if ("/travel-apply".equals(submitUrl) || submitUrl.startsWith("/travel-apply")) {
            handleTravelApply(event, terminalStatus);
        } else {
            log.debug("No handler registered for submitUrl={}, skipping", submitUrl);
        }
    }

    private void handleTravelApply(CustomFormStatusChangeEvent event, String terminalStatus) {
        Object applyIdObj = event.getFormData().get("applyId");
        if (applyIdObj == null) {
            log.warn("Cannot sync travel apply status: no applyId in formData, processInstanceId={}",
                    event.getProcessInstanceId());
            return;
        }

        Long applyId = Long.valueOf(String.valueOf(applyIdObj));
        BizTravelApply apply = travelApplyService.getById(applyId);
        if (apply == null) {
            log.warn("Travel apply not found: applyId={}", applyId);
            return;
        }

        // "1" = APPROVED, "2" = REJECTED (aligned with FlowInstanceState codes)
        String newStatus = "1".equals(terminalStatus) ? "APPROVED" : "REJECTED";
        apply.setStatus(newStatus);
        travelApplyService.updateById(apply);

        log.info("Synced travel apply status: applyId={}, newStatus={}, processInstanceId={}",
                applyId, newStatus, event.getProcessInstanceId());
    }
}
