package dev.savushkin.scada.mobile.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/**
 * Конфигурация Spring Data REST для админ-панели.
 * <p>
 * Базовый путь: {@code /api/v1.0.0/admin} — отдельно от ручных контроллеров.
 * Отключаем HAL (_embedded, _links) в пользу plain JSON для совместимости с React Admin.
 */
@Configuration
public class RestConfig {

    @Bean
    public RepositoryRestConfigurer repositoryRestConfigurer() {
        return new RepositoryRestConfigurer() {
            @Override
            public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config, CorsRegistry cors) {
                config.setBasePath("/api/v1.0.0/admin");
                // Отключаем HAL: возвращаем plain JSON вместо application/hal+json
                config.useHalAsDefaultJsonMediaType(false);
                // Не экспонируем ID в ответе (React Admin использует self-ссылку или поле id)
                config.exposeIdsFor(
                        dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RoleEntity.class,
                        dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.WorkshopEntity.class,
                        dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity.class,
                        dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceTypeEntity.class,
                        dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity.class,
                        dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity.class,
                        dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity.class,
                        dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserAssignmentEntity.class,
                        dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserNotificationSettingsEntity.class
                );
            }
        };
    }
}
