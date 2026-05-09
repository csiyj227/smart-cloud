package com.smart.common.log.aspect;

import com.smart.common.core.auth.AuthHeaders;
import com.smart.common.core.tenant.TenantContext;
import com.smart.common.core.web.HttpRequestHelper;
import com.smart.common.log.annotation.AuditTrace;
import com.smart.common.log.event.AuditTraceEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP aspect that captures audit trails from {@link AuditTrace}-annotated
 * methods and publishes them as {@link AuditTraceEvent} for async persistence.
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuditTraceAspect {

    @Around("@annotation(auditTrace)")
    public Object around(ProceedingJoinPoint point, AuditTrace auditTrace) throws Throwable {
        long startTime = System.currentTimeMillis();
        String exception = null;
        Object result;

        try {
            result = point.proceed();
        } catch (Throwable e) {
            exception = e.getMessage();
            throw e;
        } finally {
            try {
                long executionTime = System.currentTimeMillis() - startTime;
                publishLog(point, auditTrace, executionTime, exception);
            } catch (Exception e) {
                log.warn("Failed to publish audit trace", e);
            }
        }

        return result;
    }

    private void publishLog(ProceedingJoinPoint point, AuditTrace auditTrace,
                            long executionTime, String exception) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String className = signature.getDeclaringTypeName();
        String methodName = signature.getName();

        String requestUri = "";
        String httpMethod = "";
        String remoteAddr = "";
        String serviceId = "";

        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                requestUri = request.getRequestURI();
                httpMethod = request.getMethod();
                remoteAddr = HttpRequestHelper.clientIp();
                serviceId = request.getHeader("X-Service-Id");
            }
        } catch (Exception e) {
            log.trace("Could not extract request info for audit trace", e);
        }

        String params = "";
        try {
            Object[] args = point.getArgs();
            if (args != null && args.length > 0) {
                params = java.util.Arrays.toString(args);
                if (params.length() > 2000) {
                    params = params.substring(0, 2000);
                }
            }
        } catch (Exception e) {
            log.trace("Could not extract params for audit trace", e);
        }

        String traceId = MDC.get("traceId");

        String createBy = null;
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                createBy = attrs.getRequest().getHeader(AuthHeaders.USERNAME);
            }
        } catch (Exception ignored) { }

        Long tenantId = TenantContext.get().orElse(null);

        new AuditTraceEvent(
                auditTrace.value(), serviceId, remoteAddr, requestUri,
                httpMethod, className, methodName, params,
                executionTime, exception, traceId, createBy, tenantId
        ).publish();
    }
}
