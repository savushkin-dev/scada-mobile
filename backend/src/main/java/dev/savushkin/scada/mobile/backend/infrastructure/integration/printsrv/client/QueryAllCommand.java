package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PrintSrvPollingScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Команда QueryAll для получения полного состояния PrintSrv.
 * <p>
 * Выполняет опрос всех units и их свойств через socket-соединение.
 * Используется в двух сценариях:
 * <ul>
 *   <li><b>Автоматический опрос</b>: {@link PrintSrvPollingScheduler}
 *       вызывает с интервалом, заданным в конфигурации (<code>printsrv.polling.fixed-delay-ms</code>),
 *       для обновления snapshot</li>
 *   <li><b>По требованию</b>: может быть вызвана явно, если нужны актуальные данные</li>
 * </ul>
 * <p>
 * Формат запроса: <code>{"DeviceName":"Line","Command":"QueryAll"}</code><br>
 * Формат ответа: полный JSON со всеми units, их состояниями и свойствами
 */
@Component
public class QueryAllCommand extends AbstractSocketCommand<QueryAllRequestDTO, QueryAllResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(QueryAllCommand.class);

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param socketTransport транспорт для socket-соединения
     * @param objectMapper    JSON маппер для сериализации/десериализации
     */
    public QueryAllCommand(SocketTransport socketTransport, ObjectMapper objectMapper) {
        super(socketTransport, objectMapper);
        log.info("QueryAllCommand initialized");
    }

    /**
     * Возвращает класс DTO ответа для десериализации.
     *
     * @return класс QueryAllResponseDTO
     */
    @Override
    protected Class<QueryAllResponseDTO> getClassResponse() {
        return QueryAllResponseDTO.class;
    }
}
