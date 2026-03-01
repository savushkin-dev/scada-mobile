package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.mock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Конфигурационный активатор mock-инфраструктуры PrintSrv для профиля {@code dev}.
 *
 * <h3>Ответственность</h3>
 * <ul>
 *   <li>Активирует {@link MockPrintSrvProperties} как типизированный бин свойств.</li>
 *   <li>Ограничивает все mock-компоненты пакета профилем {@code dev}:
 *       в {@code prod} ни один из них не создаётся.</li>
 * </ul>
 *
 * <h3>Что активируется автоматически при наличии этого класса</h3>
 * Spring Component Scan подхватывает {@link MockPrintSrvClientRegistry}
 * и {@link MockStateSimulator} как {@code @Component}({@code @Profile("dev")}).
 * Планировщик для {@code MockStateSimulator} уже включён через {@code @EnableScheduling}
 * в {@code Application.java} — повторно здесь НЕ указываем.
 */
@Configuration
@Profile("dev")
@EnableConfigurationProperties(MockPrintSrvProperties.class)
public class MockPrintSrvConfig {
    // Намеренно пустой — вся логика инициализации вынесена в @PostConstruct компонентов.
}
