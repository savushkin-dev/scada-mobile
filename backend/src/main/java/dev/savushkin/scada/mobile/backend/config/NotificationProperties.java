package dev.savushkin.scada.mobile.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Типизированная конфигурация производственных уведомлений.
 * <p>
 * Секция {@code notifications} в {@code application.yaml}.
 * Определяет привязки пользователей к аппаратам и режим доступа по умолчанию.
 *
 * <h3>Пример конфигурации</h3>
 * <pre>
 * notifications:
 *   defaultAccess: all       # true = любой пользователь имеет доступ ко всем аппаратам
 *   assignments:
 *     - userId: "ivanov"
 *       unitIds: ["trepko1", "trepko2", "hassia1"]
 *     - userId: "petrov"
 *       unitIds: ["hassia2", "hassia3"]
 * </pre>
 *
 * <p>Активируется через {@link NotificationInfrastructureConfig}.
 */
@ConfigurationProperties(prefix = "notifications")
public class NotificationProperties {

    /**
     * Если {@code true} — любой пользователь имеет право отправлять и получать
     * уведомления от любых аппаратов. Исключает проверку {@code assignments}.
     * <p>
     * Режим разработки/тестирования: в продакшене должен быть {@code false}.
     */
    private boolean defaultAccess = false;

    /**
     * Список явных привязок пользователей к аппаратам.
     * Используется, когда {@code defaultAccess = false}.
     */
    private List<AssignmentProperties> assignments = new ArrayList<>();

    public boolean isDefaultAccess() { return defaultAccess; }
    public void setDefaultAccess(boolean defaultAccess) { this.defaultAccess = defaultAccess; }

    public List<AssignmentProperties> getAssignments() { return assignments; }
    public void setAssignments(List<AssignmentProperties> assignments) { this.assignments = assignments; }

    /**
     * Привязка одного пользователя к списку аппаратов.
     *
     * <p>Поле {@code unitIds} содержит идентификаторы PrintSrv-инстансов
     * (те же, что в {@code printsrv.instances[].id}), за которыми закреплён работник.
     * Работник одновременно является подписчиком этих аппаратов.
     */
    public static class AssignmentProperties {
        private String userId = "";
        private List<String> unitIds = new ArrayList<>();

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public List<String> getUnitIds() { return unitIds; }
        public void setUnitIds(List<String> unitIds) { this.unitIds = unitIds; }
    }
}
