package dev.savushkin.scada.mobile.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                // Не падать при неизвестных полях в JSON
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Не падать при пустых объектах
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // Читабельный вывод с отступами (для разработки)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }
}
