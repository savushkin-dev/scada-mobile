package dev.savushkin.scada.mobile.backend.services.polling;

import dev.savushkin.scada.mobile.backend.client.QueryAllCommand;
import dev.savushkin.scada.mobile.backend.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Фасад для выполнения SCADA/PrintSrv команд.
 * <p>
 * Выносит из polling-цикла знание о конкретных командах и форматах запросов/DTO.
 * Это упрощает тестирование цикла и централизует обработку/логирование вызовов команд.
 */
@Service
public class ScadaCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScadaCommandExecutor.class);

    private final QueryAllCommand queryAllCommand;

    public ScadaCommandExecutor(QueryAllCommand queryAllCommand) {
        this.queryAllCommand = Objects.requireNonNull(queryAllCommand, "queryAllCommand");
        log.info("ScadaCommandExecutor initialized");
    }

    /**
     * Выполняет QueryAll и возвращает snapshot состояния PrintSrv.
     *
     * @throws Exception если команда не выполнилась (например, проблемы с socket/десериализацией)
     */
    public QueryAllResponseDTO queryAllSnapshot() throws Exception {
        QueryAllRequestDTO request = new QueryAllRequestDTO("Line", "QueryAll");
        log.debug("Executing QueryAll command to PrintSrv");
        QueryAllResponseDTO snapshot = queryAllCommand.execute(request);
        log.debug("Received snapshot from PrintSrv with {} units", snapshot.units().size());
        return snapshot;
    }
}
