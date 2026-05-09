package com.smart.common.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method for automatic audit-trail capture.
 * The aspect records the operation details and publishes them
 * asynchronously for persistence.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditTrace {

    /**
     * Human-readable description of the operation, e.g. "Create user".
     */
    String value() default "";
}
