package dev.savushkin.scada.mobile.backend.client;

import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsResponseDTO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// TODO: выполнять запрос раз в секунду и сохранять значения
@Component
public class SetUnitVars extends AbstractSocketCommand<SetUnitVarsRequestDTO, SetUnitVarsResponseDTO> {

    public SetUnitVars(SocketTransport socketTransport, ObjectMapper objectMapper) {
        super(socketTransport, objectMapper);
    }

    @Override
    protected Class<SetUnitVarsResponseDTO> getClassResponse() {
        return SetUnitVarsResponseDTO.class;
    }
}
