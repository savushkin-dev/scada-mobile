package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.ApiMapper;
import dev.savushkin.scada.mobile.backend.api.dto.ChangeCommandResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueryStateResponseDTO;
import dev.savushkin.scada.mobile.backend.application.ScadaApplicationService;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * Сервис для предоставления данных клиентам через REST API.
 * <p>
 * Этот сервис является адаптером между API layer и application layer.
 * Он использует:
 * <ul>
 *   <li>{@link ScadaApplicationService} - для бизнес-операций</li>
 *   <li>{@link ApiMapper} - для преобразования domain моделей в API DTO</li>
 * </ul>
 * <p>
 * Основные функции:
 * <ul>
 *   <li><b>QueryAll</b>: читает состояние из application service и преобразует в API DTO</li>
 *   <li><b>SetUnitVars</b>: принимает команду от клиента и передает в application service</li>
 * </ul>
 * <p>
 * Архитектурные преимущества нового дизайна:
 * <ul>
 *   <li>API контракт независим от внутренней модели</li>
 *   <li>Изменения в domain не влияют на REST API</li>
 *   <li>Легко тестировать (можно mock application service)</li>
 * </ul>
 */
@Service
@Validated
public class CommandsService {

    private static final Logger log = LoggerFactory.getLogger(CommandsService.class);

    private final ScadaApplicationService applicationService;
    private final ApiMapper apiMapper;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param applicationService application service для бизнес-операций
     * @param apiMapper          mapper для преобразования domain → API DTO
     */
    public CommandsService(
            ScadaApplicationService applicationService,
            ApiMapper apiMapper
    ) {
        this.applicationService = applicationService;
        this.apiMapper = apiMapper;
        log.debug("CommandsService initialized");
    }

    /**
     * Получает текущий snapshot состояния SCADA системы.
     * <p>
     * Данные берутся из application service, который читает их из store.
     * Store автоматически обновляется через polling scheduler с интервалом scan cycle.
     * <p>
     * Snapshot содержит актуальное состояние на момент последнего scan cycle.
     * Изменения, сделанные через {@link #setUnitVars(int, int)}, появятся здесь
     * после следующего scan cycle (eventual consistency). Интервал scan cycle
     * настраивается через {@code printsrv.polling.fixed-delay-ms}.
     *
     * @return API DTO со состоянием SCADA системы (все units и их свойства)
     * @throws IllegalStateException если snapshot ещё не загружен (приложение только запустилось)
     */
    public QueryStateResponseDTO queryAll() {
        log.debug("Processing queryAll request");

        // Get domain model from application service
        DeviceSnapshot snapshot = applicationService.getCurrentState();

        // Convert to API DTO
        QueryStateResponseDTO response = apiMapper.toApiQueryStateResponse(snapshot);

        log.debug("QueryAll request processed successfully with {} units", snapshot.getUnitCount());
        return response;
    }

    /**
     * Добавляет команду SetUnitVars в буфер для выполнения в следующем Scan Cycle.
     * <p>
     * Метод возвращает управление немедленно, не дожидаясь записи в SCADA/PrintSrv.
     * Команда будет выполнена в следующем scan cycle (eventual consistency).
     * Интервал scan cycle настраивается через {@code printsrv.polling.fixed-delay-ms}.
     * <p>
     * Клиент может проверить результат выполнения через {@link #queryAll()}
     * после следующего scan cycle.
     * <p>
     * Архитектурные гарантии:
     * <ul>
     *   <li><b>Last-Write-Wins</b>: если для одного unit отправлено несколько команд,
     *       будет записана только последняя</li>
     *   <li><b>Eventual Consistency</b>: изменения видны после следующего scan cycle (интервал задаётся конфигом)</li>
     *   <li><b>No Retry</b>: если запись не удалась, команда теряется
     *       (клиент может повторить запрос)</li>
     * </ul>
     *
     * @param unit  номер юнита (1-based, например: 1 = u1, 2 = u2)
     * @param value новое значение команды (целое число)
     * @return acknowledgment ответ с переданными значениями (НЕ реальное состояние из SCADA)
     * @throws dev.savushkin.scada.mobile.backend.exception.BufferOverflowException если буфер переполнен
     */
    public ChangeCommandResponseDTO setUnitVars(
            @Positive @Min(1) int unit,
            @Positive @Min(1) int value
    ) {
        log.debug("Processing setUnitVars request: unit={}, value={}", unit, value);

        // Submit command to application service
        applicationService.submitWriteCommand(unit, value);

        // Create acknowledgment response (command accepted, will be executed later)
        ChangeCommandResponseDTO response = apiMapper.toApiChangeCommandResponse(unit, value);

        log.debug("SetUnitVars command accepted: unit={}, value={} (will be executed in next scan cycle)",
                unit, value);

        return response;
    }
}
