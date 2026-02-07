package dev.savushkin.scada.mobile.backend.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Абстрактный базовый класс для команд PrintSrv через socket-соединение.
 * <p>
 * Реализует паттерн Template Method для выполнения команд:
 * <ol>
 *   <li>Сериализация запроса в JSON</li>
 *   <li>Отправка через SocketTransport</li>
 *   <li>Получение ответа</li>
 *   <li>Десериализация ответа в DTO</li>
 * </ol>
 * <p>
 * Наследники определяют только тип ответа через {@link #getClassResponse()}.
 *
 * @param <T> тип DTO запроса (например, QueryAllRequestDTO)
 * @param <R> тип DTO ответа (например, QueryAllResponseDTO)
 */
public abstract class AbstractSocketCommand<T, R> {

    private static final Logger log = LoggerFactory.getLogger(AbstractSocketCommand.class);

    private final SocketTransport socketTransport;
    private final ObjectMapper objectMapper;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param socketTransport транспорт для работы с socket
     * @param objectMapper    Jackson маппер для JSON сериализации/десериализации
     */
    public AbstractSocketCommand(SocketTransport socketTransport, ObjectMapper objectMapper) {
        this.socketTransport = socketTransport;
        this.objectMapper = objectMapper;
    }

    /**
     * Выполняет команду PrintSrv: сериализует запрос, отправляет,
     * получает и десериализует ответ.
     * <p>
     * Все шаги логируются для отладки и мониторинга.
     *
     * @param request объект запроса (DTO)
     * @return объект ответа (DTO)
     * @throws IOException              если произошла ошибка связи или чтения/записи
     * @throws IllegalArgumentException если ответ от PrintSrv пустой
     */
    public R execute(T request) throws IOException {
        // Определяем тип команды для логов
        log.debug("Executing command: {}", getClassResponse().getSimpleName().replace("ResponseDTO", ""));

        // Сериализуем запрос в JSON
        String jsonRequest = objectMapper.writeValueAsString(request);
        log.trace("Serialized request JSON: {}", jsonRequest);

        // Отправляем запрос через socket
        socketTransport.sendRequest(jsonRequest);
        log.trace("Request sent to PrintSrv via socket");

        // Получаем ответ от PrintSrv
        String response = socketTransport.getResponse();
        log.trace("Received response from PrintSrv, length: {} bytes", response.length());

        // Проверяем, что ответ не пустой
        if (response.isEmpty()) {
            log.error("Received empty response from PrintSrv");
            throw new IllegalArgumentException("Response is empty");
        }

        // Десериализуем JSON ответ в DTO
        R parsedResponse = objectMapper.readValue(response, getClassResponse());
        log.debug("Response successfully parsed to {}", getClassResponse().getSimpleName());

        return parsedResponse;
    }

    /**
     * Возвращает класс DTO ответа для десериализации.
     * <p>
     * Должен быть реализован в наследниках.
     *
     * @return класс DTO ответа
     */
    protected abstract Class<R> getClassResponse();
}
