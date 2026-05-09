package com.smart.common.feign.interceptor;

import com.smart.common.core.auth.AuthHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign interceptor that propagates authentication tokens and
 * request context (tenant, user, trace) to downstream services.
 */
@Slf4j
public class ContextPropagateInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return;
            }

            var request = attrs.getRequest();

            propagate(request, template, "Authorization");
            propagate(request, template, AuthHeaders.TENANT_ID);
            propagate(request, template, AuthHeaders.USER_ID);
            propagate(request, template, AuthHeaders.USERNAME);
            propagate(request, template, AuthHeaders.TRACE_ID);

        } catch (Exception e) {
            log.warn("Context propagation failed for Feign request: {}", e.getMessage());
        }
    }

    private void propagate(jakarta.servlet.http.HttpServletRequest request,
                           RequestTemplate template, String headerName) {
        String value = request.getHeader(headerName);
        if (value != null) {
            template.header(headerName, value);
        }
    }
}
