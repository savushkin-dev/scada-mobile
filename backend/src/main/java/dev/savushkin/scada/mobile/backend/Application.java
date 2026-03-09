package dev.savushkin.scada.mobile.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс Spring Boot приложения SCADA Mobile Backend.
 * <p>
 * Приложение предоставляет REST API для мониторинга состояния аппаратов
 * производственных линий через PrintSrv. Основные функции:
 * <ul>
 *   <li>Независимый polling состояния всех инстансов PrintSrv (QueryAll)</li>
 *   <li>Хранение per-instance snapshot-ов состояния в памяти</li>
 *   <li>REST API для цехов и аппаратов</li>
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
