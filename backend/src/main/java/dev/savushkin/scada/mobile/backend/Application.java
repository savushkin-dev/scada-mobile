package dev.savushkin.scada.mobile.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс Spring Boot приложения SCADA Mobile Backend.
 * <p>
 * Приложение предоставляет REST API для взаимодействия с PrintSrv системой
 * через socket-соединение. Основные функции:
 * <ul>
 *   <li>Периодический опрос состояния PrintSrv (QueryAll)</li>
 *   <li>Изменение значений в PrintSrv (SetUnitVars)</li>
 *   <li>Хранение snapshot состояния в памяти</li>
 * </ul>
 * <p>
 * Аннотация {@code @EnableScheduling} включает поддержку планировщика
 * для автоматического опроса PrintSrv.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    /**
     * Точка входа в приложение.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        log.info("Starting SCADA Mobile Backend Application");
        SpringApplication.run(Application.class, args);
        log.info("SCADA Mobile Backend Application started successfully");
    }

}
