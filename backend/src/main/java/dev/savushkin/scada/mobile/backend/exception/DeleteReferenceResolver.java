package dev.savushkin.scada.mobile.backend.exception;

import dev.savushkin.scada.mobile.backend.api.dto.ReferenceDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserAssignmentEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserNotificationSettingsEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceCatalogJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserAssignmentJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserNotificationSettingsJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Извлекает из {@link DataIntegrityViolationException} (FK-violation, SQLState 23503)
 * список записей, ссылающихся на удаляемую, чтобы клиент мог показать,
 * кто именно использует запись.
 * <p>
 * Имя FK-констрейнта и detail берутся из {@link ServerErrorMessage} PostgreSQL.
 * По имени констрейнта выбирается запрос к соответствующему репозиторию
 * (первые 10 записей, сортировка по id).
 * <p>
 * Компонент «безопасный»: при неизвестном констрейнте, нераспарсившемся detail
 * или любой внутренней ошибке возвращает {@code null} — обработчик исключений
 * должен в этом случае отдать обычный ответ без ссылок.
 */
@Slf4j
@Component
public class DeleteReferenceResolver {

    /**
     * Detail PostgreSQL имеет вид:
     * {@code Key (role_id)=(3) is still referenced from table "users".}
     */
    private static final Pattern DETAIL_KEY_PATTERN = Pattern.compile("\\((\\w+)\\)=\\((\\d+)\\)");

    // Имена констрейнтов — из миграций V1__init.sql и V6__refactor_device_catalog.sql.
    // PostgreSQL складывает некавыченные идентификаторы в нижний регистр,
    // поэтому сравнение ведётся в lower case.
    private static final String FK_USERS_ON_ROLE = "fk_users_on_role";
    private static final String FK_UNITS_ON_WORKSHOP = "fk_units_on_workshop";
    private static final String FK_USER_UNIT_ASSIGNMENTS_ON_USER = "fk_user_unit_assignments_on_user";
    private static final String FK_USER_UNIT_ASSIGNMENTS_ON_UNIT = "fk_user_unit_assignments_on_unit";
    private static final String FK_USER_NOTIFICATION_SETTINGS_ON_USER = "fk_user_notification_settings_on_user";
    private static final String FK_USER_NOTIFICATION_SETTINGS_ON_UNIT = "fk_user_notification_settings_on_unit";
    private static final String FK_DEVICE_CATALOG_ON_TYPE = "fk_device_catalog_on_type";
    private static final String FK_UNIT_DEVICES_ON_UNIT = "fk_unit_devices_on_unit";
    private static final String FK_UNIT_DEVICES_ON_CATALOG = "fk_unit_devices_on_catalog";

    private static final String TYPE_USERS = "users";
    private static final String TYPE_UNITS = "units";
    private static final String TYPE_DEVICE_CATALOG = "device-catalog";
    private static final String TYPE_USER_ASSIGNMENTS = "user-assignments";
    private static final String TYPE_USER_NOTIFICATION_SETTINGS = "user-notification-settings";
    private static final String TYPE_UNIT_DEVICES = "unit-devices";

    private static final String LABEL_USERS = "Сотрудники";
    private static final String LABEL_UNITS = "Автоматы";
    private static final String LABEL_DEVICE_CATALOG = "Каталог устройств";
    private static final String LABEL_USER_ASSIGNMENTS = "Привязки автоматов";
    private static final String LABEL_USER_NOTIFICATION_SETTINGS = "Настройки уведомлений";
    private static final String LABEL_UNIT_DEVICES = "Устройства";

    private final UserJpaRepository userRepository;
    private final UnitJpaRepository unitRepository;
    private final UserAssignmentJpaRepository assignmentRepository;
    private final UserNotificationSettingsJpaRepository notificationSettingsRepository;
    private final DeviceJpaRepository deviceRepository;
    private final DeviceCatalogJpaRepository deviceCatalogRepository;

    public DeleteReferenceResolver(UserJpaRepository userRepository,
                                   UnitJpaRepository unitRepository,
                                   UserAssignmentJpaRepository assignmentRepository,
                                   UserNotificationSettingsJpaRepository notificationSettingsRepository,
                                   DeviceJpaRepository deviceRepository,
                                   DeviceCatalogJpaRepository deviceCatalogRepository) {
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.assignmentRepository = assignmentRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.deviceRepository = deviceRepository;
        this.deviceCatalogRepository = deviceCatalogRepository;
    }

    /**
     * Возвращает список записей (до 10), ссылающихся на удаляемую,
     * или {@code null}, если определить их невозможно.
     */
    public List<ReferenceDTO> resolve(DataIntegrityViolationException e) {
        try {
            PSQLException psqlException = findPsqlException(e);
            if (psqlException == null) {
                return null;
            }
            ServerErrorMessage serverError = psqlException.getServerErrorMessage();
            if (serverError == null || serverError.getConstraint() == null || serverError.getDetail() == null) {
                return null;
            }
            Long deletedId = parseDeletedId(serverError.getDetail());
            if (deletedId == null) {
                return null;
            }
            String constraint = serverError.getConstraint().toLowerCase(Locale.ROOT);
            return switch (constraint) {
                case FK_USERS_ON_ROLE -> userRepository.findTop10ByRole_IdOrderByIdAsc(deletedId).stream()
                        .map(this::toUserReference)
                        .toList();
                case FK_UNITS_ON_WORKSHOP -> unitRepository.findTop10ByWorkshop_IdOrderByIdAsc(deletedId).stream()
                        .map(this::toUnitReference)
                        .toList();
                case FK_USER_UNIT_ASSIGNMENTS_ON_USER ->
                        assignmentRepository.findTop10ByUser_IdOrderByIdAsc(deletedId).stream()
                                .map(this::toAssignmentReference)
                                .toList();
                case FK_USER_UNIT_ASSIGNMENTS_ON_UNIT ->
                        assignmentRepository.findTop10ByUnit_IdOrderByIdAsc(deletedId).stream()
                                .map(this::toAssignmentReference)
                                .toList();
                case FK_USER_NOTIFICATION_SETTINGS_ON_USER ->
                        notificationSettingsRepository.findTop10ByUser_IdOrderByIdAsc(deletedId).stream()
                                .map(this::toNotificationSettingsReference)
                                .toList();
                case FK_USER_NOTIFICATION_SETTINGS_ON_UNIT ->
                        notificationSettingsRepository.findTop10ByUnit_IdOrderByIdAsc(deletedId).stream()
                                .map(this::toNotificationSettingsReference)
                                .toList();
                case FK_DEVICE_CATALOG_ON_TYPE ->
                        deviceCatalogRepository.findTop10ByType_IdOrderByIdAsc(deletedId).stream()
                                .map(this::toDeviceCatalogReference)
                                .toList();
                case FK_UNIT_DEVICES_ON_UNIT -> deviceRepository.findTop10ByUnit_IdOrderByIdAsc(deletedId).stream()
                        .map(this::toDeviceReference)
                        .toList();
                case FK_UNIT_DEVICES_ON_CATALOG -> deviceRepository.findTop10ByCatalog_IdOrderByIdAsc(deletedId).stream()
                        .map(this::toDeviceReference)
                        .toList();
                default -> {
                    log.debug("Unknown FK constraint '{}', references are not resolved", constraint);
                    yield null;
                }
            };
        } catch (Exception ex) {
            log.warn("Failed to resolve delete references: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private PSQLException findPsqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof PSQLException psqlException) {
                return psqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    private Long parseDeletedId(String detail) {
        Matcher matcher = DETAIL_KEY_PATTERN.matcher(detail);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(2));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ReferenceDTO toUserReference(UserEntity user) {
        return new ReferenceDTO(TYPE_USERS, LABEL_USERS, user.getId(), user.getFullName());
    }

    private ReferenceDTO toUnitReference(UnitEntity unit) {
        return new ReferenceDTO(TYPE_UNITS, LABEL_UNITS, unit.getId(), unit.getName());
    }

    private ReferenceDTO toDeviceCatalogReference(DeviceCatalogEntity catalog) {
        return new ReferenceDTO(TYPE_DEVICE_CATALOG, LABEL_DEVICE_CATALOG, catalog.getId(), catalog.getName());
    }

    private ReferenceDTO toAssignmentReference(UserAssignmentEntity assignment) {
        return new ReferenceDTO(TYPE_USER_ASSIGNMENTS, LABEL_USER_ASSIGNMENTS,
                assignment.getId(), userUnitName(assignment.getUser(), assignment.getUnit()));
    }

    private ReferenceDTO toNotificationSettingsReference(UserNotificationSettingsEntity settings) {
        return new ReferenceDTO(TYPE_USER_NOTIFICATION_SETTINGS, LABEL_USER_NOTIFICATION_SETTINGS,
                settings.getId(), userUnitName(settings.getUser(), settings.getUnit()));
    }

    private ReferenceDTO toDeviceReference(DeviceEntity device) {
        String unitName = device.getUnit() != null ? device.getUnit().getName() : "?";
        String catalogName = device.getCatalog() != null ? device.getCatalog().getName() : "?";
        return new ReferenceDTO(TYPE_UNIT_DEVICES, LABEL_UNIT_DEVICES,
                device.getId(), unitName + " — " + catalogName);
    }

    private String userUnitName(UserEntity user, UnitEntity unit) {
        String userName = user != null ? user.getFullName() : "?";
        String unitName = unit != null ? unit.getName() : "?";
        return userName + " — " + unitName;
    }
}
