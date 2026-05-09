package com.smart.common.log.event;

import com.smart.common.core.spring.ApplicationContextProvider;
import lombok.Getter;

/**
 * Event published after a controller method execution for audit logging.
 */
@Getter
public class AuditTraceEvent {

    private final String title;
    private final String serviceId;
    private final String remoteAddr;
    private final String requestUri;
    private final String httpMethod;
    private final String className;
    private final String methodName;
    private final String params;
    private final long executionTime;
    private final String exception;
    private final String traceId;
    private final String createBy;
    private final Long tenantId;

    public AuditTraceEvent(String title, String serviceId, String remoteAddr,
                           String requestUri, String httpMethod, String className,
                           String methodName, String params, long executionTime,
                           String exception, String traceId, String createBy,
                           Long tenantId) {
        this.title = title;
        this.serviceId = serviceId;
        this.remoteAddr = remoteAddr;
        this.requestUri = requestUri;
        this.httpMethod = httpMethod;
        this.className = className;
        this.methodName = methodName;
        this.params = params;
        this.executionTime = executionTime;
        this.exception = exception;
        this.traceId = traceId;
        this.createBy = createBy;
        this.tenantId = tenantId;
    }

    public void publish() {
        ApplicationContextProvider.publishEvent(this);
    }
}
