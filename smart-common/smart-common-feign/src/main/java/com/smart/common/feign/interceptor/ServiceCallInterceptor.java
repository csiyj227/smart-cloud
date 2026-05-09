package com.smart.common.feign.interceptor;

import com.smart.common.core.auth.AuthHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign interceptor that tags every outgoing request as an inter-service call
 * by injecting the {@link AuthHeaders#SERVICE_CALL} header.
 */
@Slf4j
public class ServiceCallInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        template.header(AuthHeaders.SERVICE_CALL, AuthHeaders.SERVICE_CALL_PRESENT);
        log.trace("Tagged Feign request as service call: {}", template.url());
    }
}
