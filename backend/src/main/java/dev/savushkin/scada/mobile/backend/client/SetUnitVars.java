package dev.savushkin.scada.mobile.backend.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Команда SetUnitVars для изменения значений в PrintSrv.
 * <p>
 * Выполняет запись новых значений в указанный unit через socket-соединение.
 * Команда работает синхронно - блокирует выполнение до получения ответа от PrintSrv.
 * <p>
 * Особенности:
 * <ul>
 *   <li><b>Синхронное выполнение</b>: вызывается только по REST запросу клиента</li>
 *   <li><b>Частичный ответ</b>: PrintSrv возвращает только измененные поля</li>
 *   <li><b>Автоматическое обновление</b>: полное состояние обновится при следующем polling (~500ms)</li>
 * </ul>
 * <p>
 * Формат запроса: <code>{"DeviceName":"Line","Unit":1,"Command":"SetUnitVars","Parameters":{"command":555}}</code><br>
 * Формат ответа: частичный JSON с измененными полями указанного unit
 */
@Component
public class SetUnitVars extends AbstractSocketCommand<SetUnitVarsRequestDTO, SetUnitVarsResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(SetUnitVars.class);

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param socketTransport транспорт для socket-соединения
     * @param objectMapper    JSON маппер для сериализации/десериализации
     */
    public SetUnitVars(SocketTransport socketTransport, ObjectMapper objectMapper) {
        super(socketTransport, objectMapper);
        log.info("SetUnitVars command initialized");
    }

    /**
     * Возвращает класс DTO ответа для десериализации.
     *
     * @return класс SetUnitVarsResponseDTO
     */
    @Override
    protected Class<SetUnitVarsResponseDTO> getClassResponse() {
        return SetUnitVarsResponseDTO.class;
    }
}
