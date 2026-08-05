package com.ppdv.api.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * 统一 JSON 时间格式为 yyyy-MM-dd HH:mm:ss（对齐若依前端展示）。
 * 用 serializers/deserializers 追加，保留 Boot 自动注册的 JavaTimeModule（Instant 等仍可序列化）。
 */
@Configuration
public class JacksonConfig {

    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(PATTERN);
        return builder -> {
            builder.simpleDateFormat(PATTERN);
            builder.timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            builder.serializers(new LocalDateTimeSerializer(fmt));
            builder.deserializers(new LocalDateTimeDeserializer(fmt));
        };
    }
}
