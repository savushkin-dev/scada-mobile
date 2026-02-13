package dev.savushkin.scada.mobile.backend.application;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;
import dev.savushkin.scada.mobile.backend.store.PendingCommandsBuffer;
import dev.savushkin.scada.mobile.backend.store.PrintSrvSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Сервис приложения для координации команд SCADA.
 * <p>
 * Этот сервис координирует взаимодействие между доменными моделями, хранилищами и внешними слоями.
 * Он реализует сценарии использования для:
 * <ul>
 *   <li>Запроса текущего состояния устройства</li>
 *   <li>Отправки команд записи</li>
 * </ul>
 * <p>
 * Этот слой отвечает за:
 * <ul>
 *   <li>Координацию доменных операций</li>
 *   <li>Управление зависимостями инфраструктуры (хранилища, буферы)</li>
 *   <li>Координацию сквозных проблем</li>
 * </ul>
 * <p>
 * Этот слой НЕ:
 * <ul>
 *   <li>Содержит бизнес-логику (она в доменных сервисах)</li>
 *   <li>Знает о DTOs (они в слоях API/PrintSrv)</li>
 *   <li>Обрабатывает детали протокола (они в интеграционных слоях)</li>
 * </ul>
 */
@Service
public class ScadaApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ScadaApplicationService.class);

    private final PrintSrvSnapshotStore snapshotStore;
    private final PendingCommandsBuffer commandBuffer;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param snapshotStore хранилище для снимков состояния устройства
     * @param commandBuffer буфер для ожидающих команд записи
     */
    public ScadaApplicationService(
            PrintSrvSnapshotStore snapshotStore,
            PendingCommandsBuffer commandBuffer
    ) {
        this.snapshotStore = snapshotStore;
        this.commandBuffer = commandBuffer;
        log.info("ScadaApplicationService initialized");
    }

    /**
     * Получает текущий снимок состояния устройства.
     * <p>
     * Снимок автоматически обновляется планировщиком опроса.
     * Изменения, внесённые через {@link #submitWriteCommand(int, int)}, будут
     * видны после следующего цикла сканирования (≤ 5 секунд).
     *
     * @return текущий снимок состояния устройства
     * @throws IllegalStateException если снимок ещё недоступен
     */
    public DeviceSnapshot getCurrentState() {
        log.debug("Reading current device state from store");
        DeviceSnapshot snapshot = snapshotStore.getSnapshot();

        if (snapshot == null) {
            log.warn("Snapshot not available - store is empty");
            throw new IllegalStateException("Device snapshot not available yet. Please wait for the first scan cycle.");
        }

        log.debug("Device state retrieved successfully with {} units", snapshot.getUnitCount());
        return snapshot;
    }

    /**
     * Отправляет команду записи для выполнения в следующем цикле сканирования.
     * <p>
     * Этот метод возвращает результат немедленно (< 50ms), без ожидания
     * выполнения команды в системе SCADA.
     * <p>
     * Команда будет выполнена в следующем цикле сканирования (≤ 5 секунд).
     * Клиенты могут проверить результат через {@link #getCurrentState()} после
     * следующего цикла сканирования.
     * <p>
     * Архитектурные гарантии:
     * <ul>
     *   <li><b>Быстрый ответ</b>: возвращает < 50ms</li>
     *   <li><b>Итоговая согласованность</b>: изменения видны в ≤ 5 секунд</li>
     *   <li><b>Last-Write-Wins</b>: если несколько команд отправлены для того же модуля,
     *       будет выполнена только последняя</li>
     * </ul>
     *
     * @param unitNumber номер модуля (индексация с 1)
     * @param value      значение команды для записи
     * @throws dev.savushkin.scada.mobile.backend.exception.BufferOverflowException если буфер переполнен
     */
    public void submitWriteCommand(int unitNumber, int value) {
        log.info("Submitting write command: unit={}, value={}", unitNumber, value);

        // Создание доменной модели команды
        WriteCommand command = new WriteCommand(
                unitNumber,
                value
        );

        // Добавление в буфер (будет обработано в следующем цикле сканирования)
        commandBuffer.add(command);
        log.debug("Command added to buffer successfully (buffer size={})", commandBuffer.size());

        log.info("Write command accepted: unit={}, value={} (will be executed in next scan cycle)",
                unitNumber, value);
    }

    /**
     * Проверяет, готова ли система обслуживать запросы.
     * <p>
     * Система готова, когда получен хотя бы один снимок состояния
     * от цикла опроса/сканирования.
     *
     * @return true, если система готова
     */
    public boolean isReady() {
        return snapshotStore.getSnapshot() != null;
    }

    /**
     * Проверяет, живо ли приложение (проверка здоровья).
     *
     * @return true, если приложение живо
     */
    public boolean isAlive() {
        return true; // Application service is always alive if it can respond
    }
}
