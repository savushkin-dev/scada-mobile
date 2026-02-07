package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.client.QueryAllCommand;
import dev.savushkin.scada.mobile.backend.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class CommandsService {

    private final QueryAllCommand queryAllCommand_new;

    public CommandsService(QueryAllCommand queryAllCommand_new) {
        this.queryAllCommand_new = queryAllCommand_new;
    }

    // TODO: QueryAllResponseDTO должен делать запрос к памяти моего приложения, в которой лежат данные из PrintSrv.
    public QueryAllResponseDTO queryAll() throws IOException {
        QueryAllRequestDTO queryAllRequestDTO = new QueryAllRequestDTO("Line", "QueryAll");
        return queryAllCommand_new.execute(queryAllRequestDTO);
    }
}
