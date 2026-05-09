package com.smart.common.idempotent.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Marks a method as idempotent — duplicate requests within the time window are rejected.
 *
 * 标记方法为幂等 — 在时间窗口内的重复请求将被拒绝。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Dedup {

    /**
     * SpEL expression for building the dedup key from method arguments.
     * Default: use all arguments joined with ":".
     */
    String key() default "";

    /**
     * Time window for deduplication.
     */
    long duration() default 5;

    /**
     * Time unit for the duration.
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * Error message returned when duplicate request is detected.
     */
    String message() default "Duplicate request, please try again later";
}