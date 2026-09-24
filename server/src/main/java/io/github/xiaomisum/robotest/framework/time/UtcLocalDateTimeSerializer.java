package io.github.xiaomisum.robotest.framework.time;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.LocalDateTime;

/** 将 API 事件时间序列化为带 Z 的 UTC ISO-8601 字符串。 */
public class UtcLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator, SerializationContext context)
            throws JacksonException {
        generator.writeString(UtcTime.toIso(value));
    }
}
