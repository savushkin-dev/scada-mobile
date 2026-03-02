package dev.savushkin.scada.mobile.backend.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit-тесты для ApiMapper.
 * <p>
 * ApiMapper сейчас является placeholder для будущих WebSocket-маппингов.
 * Тест подтверждает, что компонент создаётся корректно.
 */
class ApiMapperTest {

    @Test
    void apiMapperCanBeInstantiated() {
        ApiMapper mapper = new ApiMapper();
        assertNotNull(mapper);
    }
}
