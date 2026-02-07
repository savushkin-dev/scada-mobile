package dev.savushkin.scada.mobile.backend.client;

import dev.savushkin.scada.mobile.backend.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class QueryAllCommand_new extends AbstractSocketCommand_new<QueryAllRequestDTO, QueryAllResponseDTO> {

    public QueryAllCommand_new(SocketTransport_new socketTransport, ObjectMapper objectMapper) {
        super(socketTransport, objectMapper);
    }

    @Override
    protected Class<QueryAllResponseDTO> getClassResponse() {
        return QueryAllResponseDTO.class;
    }
}
