package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.domain.model.Workshop;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Порт чтения топологии PrintSrv из БД.
 * <p>
 * Единый источник правды о цехах, аппаратах, устройствах и сетевых адресах PrintSrv.
 * Заменяет статическую конфигурацию из {@code application.yaml}.
 */
public interface PrintSrvTopologyRepository {

    /**
     * Возвращает все активные инстансы PrintSrv с полной топологией устройств.
     */
    @NonNull List<PrintSrvInstance> findAllActiveInstances();

    /**
     * Возвращает активный инстанс по его {@code printsrv_instance_id}.
     */
    @NonNull Optional<PrintSrvInstance> findByInstanceId(@NonNull String instanceId);

    /**
     * Возвращает все активные цеха.
     */
    @NonNull List<Workshop> findAllActiveWorkshops();

    /**
     * Возвращает ETag для topology-эндпоинтов.
     * <p>
     * Вычисляется на основе SHA-256 от всех активных workshops + instances + devices.
     * Кэшируется внутри репозитория и инвалидируется при изменении данных.
     */
    @NonNull String getConfigETag();
}
