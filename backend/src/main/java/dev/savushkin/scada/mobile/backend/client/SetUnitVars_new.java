package dev.savushkin.scada.mobile.backend.client;

import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsResponseDTO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SetUnitVars_new extends AbstractSocketCommand_new<SetUnitVarsRequestDTO, SetUnitVarsResponseDTO> {

    public SetUnitVars_new(SocketTransport_new socketTransport, ObjectMapper objectMapper) {
        super(socketTransport, objectMapper);
    }

    @Override
    protected Class<SetUnitVarsResponseDTO> getClassResponse() {
        return SetUnitVarsResponseDTO.class;
    }
}
