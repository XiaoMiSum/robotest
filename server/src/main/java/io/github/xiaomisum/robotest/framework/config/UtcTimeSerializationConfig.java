package io.github.xiaomisum.robotest.framework.config;

import io.github.xiaomisum.robotest.framework.time.UtcLocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;

@Configuration(proxyBeanMethods = false)
public class UtcTimeSerializationConfig {

    @Bean
    public JacksonModule utcTimeModule() {
        SimpleModule module = new SimpleModule("utc-time");
        module.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
        return module;
    }
}
