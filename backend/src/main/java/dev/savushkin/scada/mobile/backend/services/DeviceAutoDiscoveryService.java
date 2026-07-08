package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.domain.model.CompositionDiff;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceCatalogEntity;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceCompositionChangedEvent;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceEntity;
import dev.savushkin.scada.mobile.backend.domain.model.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter.PrintSrvTopologyJpaAdapter;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceCatalogJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Сервис авто-обнаружения устройств из runtime-снапшотов PrintSrv.
 * <p>
 * При обнаружении нового устройства в runtime (которого нет в БД):
 * <ol>
 *   <li>Создаёт запись в {@code device_catalog} с {@code type=null}, {@code active=false}, {@code display_name=code}</li>
 *   <li>Создаёт связь в {@code unit_devices}</li>
 *   <li>Публикует {@link DeviceCompositionChangedEvent}</li>
 * </ol>
 * <p>
 * Устройство не отображается на странице деталей автомата до тех пор,
 * пока администратор не установит {@code type} и {@code display_name}.
 */
@Service
public class DeviceAutoDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DeviceAutoDiscoveryService.class);

    private final DeviceCompositionService compositionService;
    private final DeviceCatalogJpaRepository catalogRepository;
    private final DeviceJpaRepository deviceRepository;
    private final UnitJpaRepository unitRepository;
    private final PrintSrvTopologyJpaAdapter topologyAdapter;
    private final ApplicationEventPublisher eventPublisher;

    public DeviceAutoDiscoveryService(DeviceCompositionService compositionService,
                                      DeviceCatalogJpaRepository catalogRepository,
                                      DeviceJpaRepository deviceRepository,
                                      UnitJpaRepository unitRepository,
                                      PrintSrvTopologyJpaAdapter topologyAdapter,
                                      ApplicationEventPublisher eventPublisher) {
        this.compositionService = compositionService;
        this.catalogRepository = catalogRepository;
        this.deviceRepository = deviceRepository;
        this.unitRepository = unitRepository;
        this.topologyAdapter = topologyAdapter;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Синхронизирует устройства из runtime с БД.
     * Создаёт неактивные записи для новых устройств.
     *
     * @param instanceId идентификатор аппарата (printsrv_instance_id)
     */
    @Transactional
    public void syncRuntimeDevices(@NonNull String instanceId) {
        CompositionDiff diff = compositionService.compareWithRuntime(instanceId);

        if (diff.isEmpty()) {
            return;
        }

        log.info("[{}] Device composition diff detected — added={}, removed={}",
                instanceId, diff.added(), diff.removed());

        UnitEntity unit = unitRepository.findByPrintsrvInstanceId(instanceId).orElse(null);
        if (unit == null) {
            log.warn("[{}] Unit not found for auto-discovery", instanceId);
            return;
        }

        boolean changed = false;

        // Добавить новые устройства (есть в runtime, нет в БД)
        for (String deviceCode : diff.added()) {
            DeviceCatalogEntity catalog = catalogRepository
                    .findByCode(deviceCode)
                    .orElseGet(() -> createUnconfiguredCatalog(deviceCode));

            // Проверить, есть ли уже связь unit→catalog
            boolean alreadyLinked = deviceRepository.existsByUnitIdAndCatalogId(unit.getId(), catalog.getId());
            if (!alreadyLinked) {
                DeviceEntity device = new DeviceEntity();
                device.setUnit(unit);
                device.setCatalog(catalog);
                deviceRepository.save(device);
                changed = true;
                log.info("[{}] Auto-discovered device '{}' linked to unit {}", instanceId, deviceCode, unit.getId());
            }
        }

        if (changed) {
            topologyAdapter.invalidateETag();
            eventPublisher.publishEvent(new DeviceCompositionChangedEvent(
                    instanceId,
                    diff.added(),
                    diff.removed(),
                    Instant.now()
            ));
        }
    }

    /**
     * Создаёт неполностью сконфигурированную запись в device_catalog.
     * Требует ручной настройки администратором (type + display_name).
     */
    private DeviceCatalogEntity createUnconfiguredCatalog(@NonNull String code) {
        log.info("Auto-creating unconfigured catalog entry for code='{}'", code);
        DeviceCatalogEntity catalog = new DeviceCatalogEntity();
        catalog.setCode(code);
        catalog.setDisplayName(code); // Админ должен изменить
        catalog.setType(null);        // Тип неизвестен, админ должен выбрать
        catalog.setActive(false);     // Не отображается на странице деталей
        return catalogRepository.save(catalog);
    }
}
