package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.domain.model.AdminNotificationType;
import dev.savushkin.scada.mobile.backend.domain.model.CompositionDiff;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceCompositionChangedEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter.PrintSrvTopologyJpaAdapter;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.AdminNotificationJpaRepository;
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
import java.util.Set;

/**
 * Сервис авто-обнаружения устройств из runtime-снапшотов PrintSrv.
 * <p>
 * При обнаружении расхождений между runtime и БД:
 * <ol>
 *   <li>Для новых устройств (есть в runtime, нет в БД):
 *     <ul>
 *       <li>если устройство уже есть в справочнике — связывает его с автоматом и уведомляет админа Info;</li>
 *       <li>если устройства нет в справочнике — создаёт неактивную запись, связывает с автоматом
 *           и уведомляет админа Warning;</li>
 *     </ul>
 *   </li>
 *   <li>Для пропавших устройств (есть в БД, нет в runtime) — уведомляет админа Warning один раз
 *       до восстановления устройства.</li>
 * </ol>
 */
@Service
public class DeviceAutoDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DeviceAutoDiscoveryService.class);

    private final DeviceCompositionService compositionService;
    private final DeviceCatalogJpaRepository catalogRepository;
    private final DeviceJpaRepository deviceRepository;
    private final UnitJpaRepository unitRepository;
    private final AdminNotificationJpaRepository notificationRepository;
    private final PrintSrvTopologyJpaAdapter topologyAdapter;
    private final AdminNotificationService adminNotificationService;
    private final ApplicationEventPublisher eventPublisher;

    public DeviceAutoDiscoveryService(DeviceCompositionService compositionService,
                                      DeviceCatalogJpaRepository catalogRepository,
                                      DeviceJpaRepository deviceRepository,
                                      UnitJpaRepository unitRepository,
                                      AdminNotificationJpaRepository notificationRepository,
                                      PrintSrvTopologyJpaAdapter topologyAdapter,
                                      AdminNotificationService adminNotificationService,
                                      ApplicationEventPublisher eventPublisher) {
        this.compositionService = compositionService;
        this.catalogRepository = catalogRepository;
        this.deviceRepository = deviceRepository;
        this.unitRepository = unitRepository;
        this.notificationRepository = notificationRepository;
        this.topologyAdapter = topologyAdapter;
        this.adminNotificationService = adminNotificationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Синхронизирует устройства из runtime с БД и создаёт уведомления администратору.
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
            boolean newlyCreated = false;
            DeviceCatalogEntity catalog = catalogRepository.findByCode(deviceCode).orElse(null);

            if (catalog == null) {
                catalog = createUnconfiguredCatalog(deviceCode);
                newlyCreated = true;
            }

            // Проверить, есть ли уже связь unit→catalog
            boolean alreadyLinked = deviceRepository.existsByUnit_IdAndCatalog_Id(unit.getId(), catalog.getId());
            if (!alreadyLinked) {
                DeviceEntity device = new DeviceEntity();
                device.setUnit(unit);
                device.setCatalog(catalog);
                deviceRepository.save(device);
                changed = true;
                log.info("[{}] Auto-discovered device '{}' linked to unit {}", instanceId, deviceCode, unit.getId());
            }

            // Уведомляем только если действительно что-то изменилось:
            // - устройство только что создано в справочнике, или
            // - к автомату добавлена новая связь
            if (newlyCreated || !alreadyLinked) {
                adminNotificationService.createDeviceDiscoveredNotification(
                        instanceId, deviceCode, catalog.getId(), newlyCreated);
            }
        }

        // Уведомить об устройствах, которые есть в БД, но пропали из runtime.
        // Повторных уведомлений об одном и том же пропавшем устройстве не создаём,
        // пока существует любое уведомление об отключении этого устройства.
        // Когда устройство возвращается в runtime — старое уведомление удаляется,
        // чтобы при следующем отключении админ снова получил сигнал.
        for (String deviceCode : diff.removed()) {
            boolean alreadyNotified = notificationRepository.existsByTypeAndInstanceIdAndDeviceCode(
                    AdminNotificationType.DEVICE_DISCONNECTED, instanceId, deviceCode);
            if (!alreadyNotified) {
                adminNotificationService.createDeviceDisconnectedNotification(instanceId, deviceCode);
            }
        }

        Set<String> dbDeviceCodes = compositionService.getComposition(instanceId).allDevices();
        for (String deviceCode : dbDeviceCodes) {
            if (!diff.removed().contains(deviceCode)) {
                notificationRepository.deleteByTypeAndInstanceIdAndDeviceCode(
                        AdminNotificationType.DEVICE_DISCONNECTED, instanceId, deviceCode);
            }
        }

        if (changed) {
            topologyAdapter.invalidateETag();
        }

        // Событие остаётся для инвалидации кэшей/топологии и потенциальных других слушателей.
        // Публикуем только при реальном изменении конфигурации (добавлении связи).
        if (changed) {
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
     * Требует ручной настройки администратором (type + name).
     */
    private DeviceCatalogEntity createUnconfiguredCatalog(@NonNull String code) {
        log.info("Auto-creating unconfigured catalog entry for code='{}'", code);
        DeviceCatalogEntity catalog = new DeviceCatalogEntity();
        catalog.setCode(code);
        catalog.setName(code); // Админ должен изменить
        catalog.setType(null);        // Тип неизвестен, админ должен выбрать
        catalog.setActive(false);     // Не отображается на странице деталей
        return catalogRepository.save(catalog);
    }
}
