package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ScadaApplicationService;
import org.springframework.stereotype.Service;


/**
 * Сервис здоровья приложения.
 * <p>
 * Отделяем health-check логику от REST-контроллеров: контроллеры только адаптируют HTTP,
 * а бизнес-логика (проверки доступности/готовности) находится в сервисе.
 */
@Service
public class HealthService {

    private final ScadaApplicationService applicationService;

    public HealthService(ScadaApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * Liveness: процесс жив (JVM + Spring context).
     *
     * @return всегда true, если метод вызывается.
     */
    public boolean isAlive() {
        return applicationService.isAlive();
    }

    /**
     * Readiness: сервис "готов" отдавать данные.
     * <p>
     * Для текущей архитектуры это означает: первый snapshot уже получен из PrintSrv.
     *
     * @return true, если snapshot уже загружен.
     */
    public boolean isReady() {
        return applicationService.isReady();
    }
}
