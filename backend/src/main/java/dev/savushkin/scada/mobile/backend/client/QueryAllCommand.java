package dev.savushkin.scada.mobile.backend.client;

import dev.savushkin.scada.mobile.backend.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// TODO: выполнять запрос раз в секунду и сохранять значения
@Component
public class QueryAllCommand extends AbstractSocketCommand<QueryAllRequestDTO, QueryAllResponseDTO> {

    public QueryAllCommand(SocketTransport socketTransport, ObjectMapper objectMapper) {
        super(socketTransport, objectMapper);
    }

    @Override
    protected Class<QueryAllResponseDTO> getClassResponse() {
        return QueryAllResponseDTO.class;
    }
}
