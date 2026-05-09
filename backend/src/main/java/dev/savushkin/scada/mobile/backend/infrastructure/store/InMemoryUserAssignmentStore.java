package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.application.ports.UserAssignmentRepository;
import dev.savushkin.scada.mobile.backend.config.NotificationProperties;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory реализация {@link UserAssignmentRepository} — привязки пользователей к аппаратам.
 * <p>
 * Засеивается из {@link NotificationProperties} при старте приложения.
 * Данные берутся из YAML-конфигурации ({@code notifications.assignments}).
 *
 * <h3>Режим defaultAccess</h3>
 * Если {@code notifications.defaultAccess = true} — {@link #canSendNotification} всегда
 * возвращает {@code true}, а {@link #getSubscribedUnitIds} и {@link #getAssignedUnitIds}
 * возвращают все instanceIds из конфигурации PrintSrv.
 * <p>
 * Это режим разработки/тестирования; в продакшене {@code defaultAccess} должен быть {@code false}.
 *
 * <h3>Архитектурная роль</h3>
 * Временная реализация порта. При переходе на PostgreSQL заменяется на JPA-реализацию
 * (таблица {@code user_unit_assignments}). Бизнес-логика в {@code NotificationService}
 * не меняется.
 */
@Component
public class InMemoryUserAssignmentStore implements UserAssignmentRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryUserAssignmentStore.class);

    private final NotificationProperties notificationProps;
    private final PrintSrvProperties printSrvProps;

    /**
     * userId → unitIds (закрепление и подписка совпадают).
     */
    private Map<String, Set<String>> assignmentsByUser = Map.of();

    /** Все instanceIds из конфигурации PrintSrv (для defaultAccess). */
    private Set<String> allInstanceIds = Set.of();

    public InMemoryUserAssignmentStore(NotificationProperties notificationProps,
                                       PrintSrvProperties printSrvProps) {
        this.notificationProps = notificationProps;
        this.printSrvProps = printSrvProps;
    }

    @PostConstruct
    void init() {
        allInstanceIds = printSrvProps.getInstances().stream()
                .map(PrintSrvProperties.InstanceProperties::getId)
                .collect(Collectors.toUnmodifiableSet());

        assignmentsByUser = notificationProps.getAssignments().stream()
                .filter(a -> a.getUserId() != null && !a.getUserId().isBlank())
                .collect(Collectors.toMap(
                        NotificationProperties.AssignmentProperties::getUserId,
                        a -> Set.copyOf(a.getUnitIds()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        if (notificationProps.isDefaultAccess()) {
            log.info("Notifications: defaultAccess=ALL — all users can send/receive for all {} units",
                    allInstanceIds.size());
        } else {
            log.info("Notifications: {} user assignments loaded, defaultAccess=OFF",
                    assignmentsByUser.size());
        }
    }

    @Override
    public boolean canSendNotification(@NonNull String userId, @NonNull String unitId) {
        if (notificationProps.isDefaultAccess()) {
            return true;
        }
        Set<String> units = assignmentsByUser.get(userId);
        return units != null && units.contains(unitId);
    }

    @Override
    public @NonNull Set<String> getSubscribedUnitIds(@NonNull String userId) {
        if (notificationProps.isDefaultAccess()) {
            return allInstanceIds;
        }
        return assignmentsByUser.getOrDefault(userId, Set.of());
    }

    @Override
    public @NonNull Set<String> getAssignedUnitIds(@NonNull String userId) {
        if (notificationProps.isDefaultAccess()) {
            return allInstanceIds;
        }
        return assignmentsByUser.getOrDefault(userId, Set.of());
    }
}
