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
 * Boot-style ObjectMapper configuration used by Spring MVC and integration clients.
 */
@Configuration
public class JacksonConfig {

    private static final Logger log = LoggerFactory.getLogger(JacksonConfig.class);

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        log.info("Creating custom ObjectMapper (Boot-way with @Primary)");

        ObjectMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();

        log.debug("ObjectMapper configured: FAIL_ON_UNKNOWN_PROPERTIES=disabled");
        return mapper;
    }
}
