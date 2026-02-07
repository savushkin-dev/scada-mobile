package dev.savushkin.scada.mobile.backend.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Boot-way конфигурация Jackson ObjectMapper.
 * <p>
 * Создаем @Primary ObjectMapper bean, который Spring Boot будет использовать:
 * - В HttpMessageConverters для REST API
 * - При автоматическом внедрении через @Autowired (socket-клиент)
 * <p>
 * Преимущества:
 * - @Primary обеспечивает единую конфигурацию JSON для всего приложения
 * - Spring Boot автоматически подхватывает этот bean для REST
 * - Простота и прозрачность настройки
 */
@Configuration
public class JacksonConfig {

    private static final Logger log = LoggerFactory.getLogger(JacksonConfig.class);

    /**
     * Создает ObjectMapper с кастомными настройками.
     * Используется и для REST API (через Spring MVC), и для socket-клиента PrintSrv.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        log.info("Creating custom ObjectMapper (Boot-way with @Primary)");

        ObjectMapper mapper = JsonMapper.builder()
                // Не падать при неизвестных полях в JSON от PrintSrv
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Не падать при сериализации пустых объектов
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // Pretty-print JSON (удобно для разработки и отладки)
                // TODO: в production лучше отключить для производительности
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();

        log.debug("ObjectMapper configured: FAIL_ON_UNKNOWN_PROPERTIES=disabled, INDENT_OUTPUT=enabled");
        return mapper;
    }
}
