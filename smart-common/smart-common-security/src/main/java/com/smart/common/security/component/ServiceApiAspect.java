package com.smart.common.security.component;

import com.smart.common.core.auth.AuthHeaders;
import com.smart.common.security.annotation.ServiceApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP aspect that enforces the {@link ServiceApi} contract.
 * Rejects any request that does not carry the inter-service header.
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class ServiceApiAspect {

    @Before("@annotation(serviceApi) || @within(serviceApi)")
    public void verifyServiceCall(JoinPoint point, ServiceApi serviceApi) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }

        HttpServletRequest request = attrs.getRequest();
        String marker = request.getHeader(AuthHeaders.SERVICE_CALL);

        if (!AuthHeaders.SERVICE_CALL_PRESENT.equals(marker)) {
            log.warn("Blocked non-service call to @ServiceApi endpoint: {} {}",
                    request.getMethod(), request.getRequestURI());
            throw new AccessDeniedException(
                    "This endpoint is restricted to inter-service calls only");
        }
    }
}
