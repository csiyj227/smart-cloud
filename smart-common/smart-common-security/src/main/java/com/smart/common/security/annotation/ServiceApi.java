package com.smart.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as a service-to-service API.
 * Only requests carrying the inter-service authentication header
 * (injected by Feign and stripped by the gateway) are allowed.
 *
 * @see com.smart.common.security.component.ServiceApiAspect
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ServiceApi {

    /**
     * Description of this service API endpoint for documentation purposes.
     */
    String value() default "";
}
