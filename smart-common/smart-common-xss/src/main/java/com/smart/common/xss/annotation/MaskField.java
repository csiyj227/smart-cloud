package com.smart.common.xss.annotation;

import com.smart.common.xss.serializer.MaskStrategy;

import java.lang.annotation.*;

/**
 * Marks a field for desensitization during JSON serialization.
 *
 * 标记字段在 JSON 序列化时进行脱敏处理。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MaskField {

    /**
     * Masking strategy to apply.
     */
    MaskStrategy value() default MaskStrategy.DEFAULT;
}