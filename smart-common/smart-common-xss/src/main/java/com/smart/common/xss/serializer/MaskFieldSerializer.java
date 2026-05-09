package com.smart.common.xss.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.smart.common.xss.annotation.MaskField;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.Objects;

/**
 * Jackson serializer that applies masking based on @MaskField annotation.
 *
 * 基于 @MaskField 注解应用脱敏的 Jackson 序列化器。
 */
@NoArgsConstructor
@AllArgsConstructor
public class MaskFieldSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private MaskStrategy strategy;

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(mask(value, strategy));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        MaskField annotation = property.getAnnotation(MaskField.class);
        if (Objects.nonNull(annotation) && String.class.equals(property.getType().getRawClass())) {
            return new MaskFieldSerializer(annotation.value());
        }
        return this;
    }

    private String mask(String value, MaskStrategy strategy) {
        if (strategy == null) {
            strategy = MaskStrategy.DEFAULT;
        }
        int len = value.length();
        return switch (strategy) {
            case NAME -> maskName(value);
            case PHONE -> len > 7 ? value.substring(0, 3) + "****" + value.substring(7) : value;
            case ID_CARD -> len > 10 ? value.substring(0, 6) + "********" + value.substring(len - 4) : value;
            case EMAIL -> {
                int at = value.indexOf('@');
                yield at > 1 ? value.substring(0, 2) + "***" + value.substring(at) : value;
            }
            case BANK_CARD -> len > 8 ? "****" + value.substring(len - 4) : value;
            case ADDRESS -> len > 6 ? value.substring(0, 6) + "***" : value;
            default -> len > 2 ? value.charAt(0) + "***" + value.charAt(len - 1) : value;
        };
    }

    private String maskName(String name) {
        if (name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}